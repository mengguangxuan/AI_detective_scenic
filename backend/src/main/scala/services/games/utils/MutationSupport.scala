package services.games.utils

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.objects.*
import services.games.objects.apiTypes.*
import services.games.tables.SessionTable.SessionTable
import services.games.tables.SessionRecordTable.SessionRecordTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.tables.InterviewRequestTable.InterviewRequestTable
import services.games.tables.ReasoningRequestTable.ReasoningRequestTable
import services.system.objects.*
import services.cases.objects.investigation.TimeAssessment

private[games] object MutationSupport:
  private[games] def owned(row: Option[GameSessionRecord],user: UserId): IO[GameSessionRecord] = row match
    case Some(s) if s.owner==user => IO.pure(s)
    case _ => IO.raiseError(GameError(ErrorCode.AccessDenied,"无法访问该会话。"))

  private[games] def operationDigest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,operation: OperationId): String =
    digest(user,session,request,expected,Json.obj("kind" -> Json.fromString("Execute"),"operation" -> operation.asJson))

  private[games] def investigationDigest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,point: PointId,route: RouteId): String =
    digest(user,session,request,expected,Json.obj("kind" -> Json.fromString("Investigate"),"point" -> point.asJson,"route" -> route.asJson))

  private[games] def comparisonDigest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,
      evidence: Set[ClueId],minutes: Int,assessment: TimeAssessment): String =
    digest(user,session,request,expected,Json.obj("kind" -> Json.fromString("CompareTimes"),
      "evidence" -> evidence.toVector.sortBy(_.value).asJson,"minutes" -> minutes.asJson,"assessment" -> assessment.asJson))

  private[games] def interviewDigest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,
      witness: WitnessId,question: String,evidence: Set[ClueId]): String =
    digest(user,session,request,expected,Json.obj("kind" -> Json.fromString("AskWitness"), "witness" -> witness.asJson,
      "question" -> question.asJson,"evidence" -> evidence.toVector.sortBy(_.value).asJson))

  private[games] def reportDigest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,evidence: Set[ClueId]): String =
    digest(user,session,request,expected,Json.obj("kind" -> Json.fromString("ReportClues"),
      "evidence" -> evidence.toVector.sortBy(_.value).asJson))

  private[games] def externalCheckDigest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,check: ExternalCheckId): String =
    digest(user,session,request,expected,Json.obj("kind" -> Json.fromString("ExternalCheck"),"check" -> check.asJson))

  private[games] def advanceTimeDigest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,minutes: Int): String =
    digest(user,session,request,expected,Json.obj("kind" -> Json.fromString("AdvanceTime"),"minutes" -> minutes.asJson))

  private[games] def conclusionDigest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,
      suspects: Set[PersonId],evidence: Set[ClueId],explanation: String): String =
    digest(user,session,request,expected,Json.obj("kind" -> Json.fromString("SubmitConclusion"),
      "suspects" -> suspects.toVector.sortBy(_.value).asJson,"evidence" -> evidence.toVector.sortBy(_.value).asJson,"explanation" -> explanation.asJson))

  private[games] def reasoningDigest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,
      suspects: Set[PersonId],evidence: Set[ClueId],explanation: String): String =
    digest(user,session,request,expected,Json.obj("kind" -> Json.fromString("EvaluateReasoning"),
      "suspects" -> suspects.toVector.sortBy(_.value).asJson,"evidence" -> evidence.toVector.sortBy(_.value).asJson,"explanation" -> explanation.asJson))

  private[games] def reasoningEndingDigest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,abandon: Boolean): String =
    digest(user,session,request,expected,Json.obj("kind" -> Json.fromString("RevealReasoning"),"abandon" -> abandon.asJson))

  // Keep the exact original JSON bytes so stored receipt digests remain valid.
  private def digest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,command: Json): String =
    val payload=Json.obj("user" -> user.asJson,"session" -> session.asJson,"request" -> request.asJson,
      "expected" -> expected.asJson,"command" -> command).noSpaces
    MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)).map(b => f"${b & 0xff}%02x").mkString

  private[games] def retry(receipt: RequestReceiptRecord,digest: String): IO[CommandResult] =
    if receipt.digest==digest then IO.pure(receipt.result)
    else IO.raiseError(GameError(ErrorCode.RequestConflict,"同一请求编号的内容发生变化。"))

  private[games] def checkSequence(row: GameSessionRecord,expected: Sequence): IO[Unit] =
    IO.raiseUnless(row.sequence==expected)(GameError(ErrorCode.StaleState,"会话已发生变化，请刷新。"))

  private[games] def commit(connection: Connection,previous: GameSessionRecord,request: RequestId,digest: String,plan: ActionPlan,
      pendingInterview: Boolean = false, pendingReasoning: Boolean = false): IO[CommandResult] =
    for
      reserved <- InterviewRequestTable.find(connection,previous.id,request)
      _ <- IO.raiseWhen(reserved.nonEmpty && !pendingInterview)(GameError(ErrorCode.RequestConflict,"该请求编号已用于人物问询。"))
      reasoning <- ReasoningRequestTable.find(connection,previous.id,request)
      _ <- IO.raiseWhen(reasoning.nonEmpty && !pendingReasoning)(GameError(ErrorCode.RequestConflict,"该请求编号已用于推理评审。"))
      revision <- CaseRevisionTable.find(connection,previous.caseId,previous.caseRevision).flatMap(v => IO.fromOption(v)(GameError(ErrorCode.InvalidValue,"案件修订不存在。")))
      timed = ExternalCheckRules.afterAction(revision.content,previous,plan)
      _ <- SessionTable.update(connection,previous,timed.session)
      _ <- SessionRecordTable.insert(connection,timed.record)
      _ <- ReceiptTable.insert(connection,RequestReceiptRecord(previous.id,request,digest,1,timed.result))
    yield timed.result
