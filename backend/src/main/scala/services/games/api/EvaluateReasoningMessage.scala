package services.games.api

import cats.effect.IO
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import java.util.UUID
import scala.concurrent.duration.*
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.objects.*
import services.games.objects.apiTypes.CommandResult
import services.games.tables.SessionTable.SessionTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.tables.InterviewRequestTable.InterviewRequestTable
import services.games.tables.ReasoningRequestTable.ReasoningRequestTable
import services.games.utils.{ReasoningRules,MutationSupport}
import services.models.api.ChatCompletionClient
import services.system.api.{APIMessage,DatabaseSession}
import services.system.objects.*

final case class EvaluateReasoningMessage(userId: UserId,sessionId: SessionId,requestId: RequestId,expectedSequence: Sequence,
  suspects: Set[PersonId],evidence: Set[ClueId],explanation: String) extends APIMessage[ReasoningPreparation]:
  override def plan(connection: Connection): IO[ReasoningPreparation] =
    val digest=MutationSupport.reasoningDigest(userId,sessionId,requestId,expectedSequence,suspects,evidence,explanation)
    for
      _ <- IO.blocking(require(!connection.getAutoCommit,"Message requires transaction"))
      session <- SessionTable.lock(connection,sessionId).flatMap(MutationSupport.owned(_,userId))
      receipt <- ReceiptTable.find(connection,sessionId,requestId)
      prepared <- receipt match
        case Some(saved) => MutationSupport.retry(saved,digest).map(ReasoningPreparation.Cached.apply)
        case None =>
          for
            other <- InterviewRequestTable.find(connection,sessionId,requestId)
            _ <- IO.raiseWhen(other.nonEmpty)(GameError(ErrorCode.RequestConflict,"该请求编号已用于人物问询。"))
            now <- IO.realTime.map(_.toMillis)
            existing <- ReasoningRequestTable.find(connection,sessionId,requestId)
            prepared <- existing match
              case Some(row) if row.digest != digest => IO.raiseError(GameError(ErrorCode.RequestConflict,"同一请求编号的内容发生变化。"))
              case Some(row) if row.status != ReasoningRequestStatus.Pending || row.expectedSequence != session.sequence =>
                ReasoningRequestTable.replace(connection,row.copy(status=ReasoningRequestStatus.Cancelled),row.leaseToken)
                  .as(ReasoningPreparation.Rejected(GameError(ErrorCode.StaleState,"调查已有变化，请重新提出问题。")))
              case Some(row) if row.leaseUntilMillis > now => IO.raiseError(GameError(ErrorCode.RequestPending,"这次推理评审仍在处理中，请稍后用原请求重试。"))
              case Some(row) =>
                for
                  token <- IO(UUID.randomUUID().toString)
                  renewed = row.copy(leaseToken=token,leaseUntilMillis=now+90000)
                  _ <- ReasoningRequestTable.replace(connection,renewed,row.leaseToken)
                yield ReasoningPreparation.Reserved(renewed)
              case None =>
                for
                  _ <- MutationSupport.checkSequence(session,expectedSequence)
                  active <- ReasoningRequestTable.hasActive(connection,sessionId,now)
                  evaluating <- InterviewRequestTable.hasActive(connection,sessionId,now)
                  _ <- IO.raiseWhen(active || evaluating)(GameError(ErrorCode.RequestPending,"这场调查还有模型请求正在处理中，请稍后重试。"))
                  revision <- CaseRevisionTable.find(connection,session.caseId,session.caseRevision).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件修订不存在。")))
                  context <- IO.fromEither(ReasoningRules.context(revision.content,session,suspects,evidence,explanation))
                  token <- IO(UUID.randomUUID().toString)
                  row = ReasoningRequestRecord(sessionId,requestId,digest,expectedSequence,suspects,evidence,explanation,context,token,now+90000,ReasoningRequestStatus.Pending)
                  _ <- ReasoningRequestTable.insert(connection,row)
                yield ReasoningPreparation.Reserved(row)
          yield prepared
    yield prepared

  // This extra entry owns a real resource boundary: no JDBC connection exists during the outbound call.
  def run(databaseUrl: String,client: ChatCompletionClient): IO[CommandResult] =
    DatabaseSession.connection(databaseUrl).use(c => DatabaseSession.transaction(c)(plan(c))).flatMap {
      case ReasoningPreparation.Cached(result) => IO.pure(result)
      case ReasoningPreparation.Rejected(error) => IO.raiseError(error)
      case ReasoningPreparation.Reserved(row) =>
        for
          completion <- (for
            raw <- client.complete(ReasoningRules.judgePrompt(row))
            assessed = ReasoningRules.judge(raw,row)
            _ <- IO.whenA(assessed.isEmpty)(IO.println(s"[reasoning] Judge output rejected: ${ReasoningRules.judgeValidationSummary(raw,row)}"))
            decision <- IO.fromOption(assessed)(GameError(ErrorCode.InvalidValue,"Invalid reasoning assessment"))
            feedback <- if row.context.revision.value >= 19 && decision.checks.forall(_.satisfied) then
              IO.pure(ReasoningRules.establishedFeedback)
            else
              client.complete(ReasoningRules.guidancePrompt(row,decision)).flatMap { raw =>
                val guided=ReasoningRules.guidance(raw,row)
                IO.whenA(guided.isEmpty)(IO.println("[reasoning] Guidance output rejected: structure, text limits or evidence references.")) *>
                  IO.fromOption(guided)(GameError(ErrorCode.InvalidValue,"Invalid reasoning guidance"))
              }
          yield (decision,feedback)).timeout(65.seconds).attempt
          decision = completion.toOption.map(_._1)
          feedback = completion.toOption.map(_._2)
          failure = completion.left.toOption.map {
            case error: GameError if error.code == ErrorCode.InvalidValue => ReasoningStatus.Rejected
            case _ => ReasoningStatus.Unavailable
          }
          outcome <- DatabaseSession.connection(databaseUrl).use(c => DatabaseSession.transaction(c)(
            FinishReasoningMessage(userId,sessionId,requestId,row.leaseToken,decision,feedback,failure).plan(c)))
          result <- IO.fromEither(outcome)
        yield result
    }
object EvaluateReasoningMessage:
  given Encoder[EvaluateReasoningMessage] = deriveEncoder[EvaluateReasoningMessage]
  given Decoder[EvaluateReasoningMessage] = deriveDecoder[EvaluateReasoningMessage]
