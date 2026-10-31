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
import services.games.tables.ReasoningRequestTable.ReasoningRequestTable
import services.games.utils.{ReasoningRules,MutationSupport}
import services.system.api.APIMessage
import services.system.objects.*

// Internal completion. An error returned as Left commits cancellation before the HTTP boundary raises it.
final case class FinishReasoningMessage(userId: UserId,sessionId: SessionId,requestId: RequestId,leaseToken: String,
  decision: Option[ReasoningDecision],feedback: Option[String],failure: Option[ReasoningStatus]) extends APIMessage[Either[GameError,CommandResult]]:
  override def plan(connection: Connection): IO[Either[GameError,CommandResult]] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit,"Message requires transaction"))
      session <- SessionTable.lock(connection,sessionId).flatMap(MutationSupport.owned(_,userId))
      request <- ReasoningRequestTable.find(connection,sessionId,requestId).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"推理评审请求不存在。")))
      receipt <- ReceiptTable.find(connection,sessionId,requestId)
      now <- IO.realTime.map(_.toMillis)
      result <- receipt match
        case Some(saved) => MutationSupport.retry(saved,request.digest).map(Right.apply)
        case None if request.leaseToken != leaseToken =>
          IO.pure(Left(GameError(ErrorCode.RequestPending,"这次推理评审已由另一请求接续，请稍后用原请求重试。")))
        case None if request.status != ReasoningRequestStatus.Pending || session.sequence != request.expectedSequence ||
            session.caseId != request.context.caseId || session.caseRevision != request.context.revision || request.leaseUntilMillis <= now =>
          ReasoningRequestTable.replace(connection,request.copy(status=ReasoningRequestStatus.Cancelled),leaseToken)
            .as(Left(GameError(ErrorCode.StaleState,"调查已有变化，请重新提出问题。")))
        case None =>
          for
            revision <- CaseRevisionTable.find(connection,session.caseId,session.caseRevision).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件修订不存在。")))
            action <- IO.fromEither(ReasoningRules.finish(revision.content,session,request,decision,feedback,failure))
            _ <- ReasoningRequestTable.replace(connection,request.copy(status=ReasoningRequestStatus.Finished),leaseToken)
            response <- MutationSupport.commit(connection,session,requestId,request.digest,action,pendingReasoning=true)
          yield Right(response)
    yield result
object FinishReasoningMessage:
  given Encoder[FinishReasoningMessage] = deriveEncoder[FinishReasoningMessage]
  given Decoder[FinishReasoningMessage] = deriveDecoder[FinishReasoningMessage]
