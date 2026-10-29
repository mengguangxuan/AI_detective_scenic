package services.games.api

import cats.effect.IO
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.objects.apiTypes.CommandResult
import services.games.tables.SessionTable.SessionTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.tables.SessionRecordTable.SessionRecordTable
import services.games.tables.InterviewTurnTable.InterviewTurnTable
import services.games.utils.{FixtureRules,MutationSupport}
import services.system.api.APIMessage
import services.system.objects.*

final case class ExecuteOperationMessage(userId: UserId,sessionId: SessionId,requestId: RequestId,expectedSequence: Sequence,operation: OperationId) extends APIMessage[CommandResult]:
  override def plan(connection: Connection): IO[CommandResult] =
    val digest=MutationSupport.operationDigest(userId,sessionId,requestId,expectedSequence,operation)
    for
      _ <- IO.blocking(require(!connection.getAutoCommit,"Message requires transaction"))
      session <- SessionTable.lock(connection,sessionId).flatMap(MutationSupport.owned(_,userId))
      receipt <- ReceiptTable.find(connection,sessionId,requestId)
      result <- receipt match
        case Some(saved) => MutationSupport.retry(saved,digest)
        case None =>
          for
            _ <- MutationSupport.checkSequence(session,expectedSequence)
            revision <- CaseRevisionTable.find(connection,session.caseId,session.caseRevision).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件修订不存在。")))
            records <- SessionRecordTable.list(connection,sessionId)
            turns <- InterviewTurnTable.list(connection,sessionId)
            plan <- IO.fromEither(FixtureRules.execute(revision.content,session,requestId,operation,records,turns))
            result <- MutationSupport.commit(connection,session,requestId,digest,plan)
          yield result
    yield result

object ExecuteOperationMessage:
  given Encoder[ExecuteOperationMessage] = deriveEncoder[ExecuteOperationMessage]
  given Decoder[ExecuteOperationMessage] = deriveDecoder[ExecuteOperationMessage]
