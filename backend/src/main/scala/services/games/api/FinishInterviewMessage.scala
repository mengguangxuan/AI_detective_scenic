package services.games.api

import cats.effect.IO
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.objects.*
import services.games.objects.apiTypes.CommandResult
import services.games.tables.SessionTable.SessionTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.tables.InterviewRequestTable.InterviewRequestTable
import services.games.tables.InterviewTurnTable.InterviewTurnTable
import services.games.tables.PoliceExchangeTable.PoliceExchangeTable
import services.games.objects.PoliceExchangeRecord
import services.games.utils.{InterviewRules,MutationSupport}
import services.system.api.APIMessage
import services.system.objects.*

// Internal completion. An error returned as Left commits cancellation before the HTTP boundary raises it.
final case class FinishInterviewMessage(userId: UserId,sessionId: SessionId,requestId: RequestId,leaseToken: String,
  decision: Option[InterviewDecision],failure: Option[InterviewReplyMode]) extends APIMessage[Either[GameError,CommandResult]]:
  override def plan(connection: Connection): IO[Either[GameError,CommandResult]] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit,"Message requires transaction"))
      session <- SessionTable.lock(connection,sessionId).flatMap(MutationSupport.owned(_,userId))
      request <- InterviewRequestTable.find(connection,sessionId,requestId).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"问询请求不存在。")))
      receipt <- ReceiptTable.find(connection,sessionId,requestId)
      now <- IO.realTime.map(_.toMillis)
      result <- receipt match
        case Some(saved) => MutationSupport.retry(saved,request.digest).map(Right.apply)
        case None if request.leaseToken != leaseToken =>
          IO.pure(Left(GameError(ErrorCode.RequestPending,"这次问询已由另一请求接续，请稍后用原请求重试。")))
        case None if request.status != InterviewRequestStatus.Pending || session.sequence != request.expectedSequence ||
            session.caseId != request.context.caseId || session.caseRevision != request.context.revision || request.leaseUntilMillis <= now =>
          InterviewRequestTable.replace(connection,request.copy(status=InterviewRequestStatus.Cancelled),leaseToken)
            .as(Left(GameError(ErrorCode.StaleState,"调查已有变化，请重新提出问题。")))
        case None =>
          for
            revision <- CaseRevisionTable.find(connection,session.caseId,session.caseRevision).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件修订不存在。")))
            // Recheck authoring capabilities and references even for an internal caller.
            validDecision = decision.filter(d => InterviewRules.validDecision(d,request.context))
            safeFailure = failure.filter(m => m == InterviewReplyMode.Unavailable || m == InterviewReplyMode.Rejected)
              .orElse(if validDecision.isEmpty then Some(InterviewReplyMode.Rejected) else None)
            planned <- IO.fromEither(InterviewRules.plan(revision.content,session,request,validDecision,safeFailure))
            (action,turn) = planned
            _ <- InterviewTurnTable.insert(connection,turn)
            _ <- if revision.content.police.exists(_.point == request.context.profile.point) && turn.mode == InterviewReplyMode.Answered && turn.shownClues.nonEmpty then
              PoliceExchangeTable.insert(connection,PoliceExchangeRecord(sessionId,requestId,turn.sequence,turn.shownClues,turn.reply)) else IO.unit
            _ <- InterviewRequestTable.replace(connection,request.copy(status=InterviewRequestStatus.Finished),leaseToken)
            response <- MutationSupport.commit(connection,session,requestId,request.digest,action,pendingInterview=true)
          yield Right(response)
    yield result
object FinishInterviewMessage:
  given Encoder[FinishInterviewMessage] = deriveEncoder[FinishInterviewMessage]
  given Decoder[FinishInterviewMessage] = deriveDecoder[FinishInterviewMessage]
