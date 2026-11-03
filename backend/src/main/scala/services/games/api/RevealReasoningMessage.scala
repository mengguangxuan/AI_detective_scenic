package services.games.api

import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseTable.CaseTable
import services.games.objects.apiTypes.CommandResult
import services.games.tables.InterviewTurnTable.InterviewTurnTable
import services.games.tables.SessionTable.SessionTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.utils.{ReasoningRules, MutationSupport, RecordedInterviewRules}
import services.system.api.APIMessage
import services.system.objects.*

final case class RevealReasoningMessage(userId: UserId, sessionId: SessionId, requestId: RequestId, expectedSequence: Sequence,
  abandon: Boolean) extends APIMessage[CommandResult]:
  override def plan(connection: Connection): IO[CommandResult] =
    val digest = MutationSupport.reasoningEndingDigest(userId,sessionId,requestId,expectedSequence,abandon)
    for
      _ <- IO.blocking(require(!connection.getAutoCommit,"Message requires transaction"))
      session <- SessionTable.lock(connection,sessionId).flatMap(MutationSupport.owned(_,userId))
      receipt <- ReceiptTable.find(connection,sessionId,requestId)
      result <- receipt match
        case Some(saved) => MutationSupport.retry(saved,digest)
        case None =>
          for
            _ <- MutationSupport.checkSequence(session,expectedSequence)
            caseRecord <- CaseTable.find(connection,session.caseId).flatMap(value =>
              IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件不存在。")))
            turns <- InterviewTurnTable.list(connection,sessionId)
            plan <- IO.fromEither(ReasoningRules.reveal(RecordedInterviewRules.content(caseRecord.content,turns),session,requestId,abandon))
            result <- MutationSupport.commit(connection,session,requestId,digest,plan)
          yield result
    yield result
object RevealReasoningMessage:
  given Encoder[RevealReasoningMessage] = deriveEncoder[RevealReasoningMessage]
  given Decoder[RevealReasoningMessage] = deriveDecoder[RevealReasoningMessage]
