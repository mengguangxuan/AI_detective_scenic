package services.games.api

import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.cases.objects.OperationId
import services.games.objects.FixtureCommand
import services.games.objects.apiTypes.ExecuteOperationResponse
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.utils.{FixtureRules, MutationSupport}
import services.system.api.APIMessage
import services.system.objects.*

final case class ExecuteOperationMessage(userId: UserId, sessionId: SessionId,
    requestId: RequestId, expectedSequence: Sequence, operation: OperationId)
    extends APIMessage[ExecuteOperationResponse]:
  override def plan(connection: Connection): IO[ExecuteOperationResponse] =
    val digest = MutationSupport.digest(userId, sessionId, requestId, expectedSequence, FixtureCommand.Execute(operation))
    for
      _ <- IO.blocking(require(!connection.getAutoCommit, "Message requires transaction"))
      session <- MutationSupport.lockOwned(connection, userId, sessionId)
      receipt <- ReceiptTable.find(connection, sessionId, requestId)
      result <- receipt match
        case Some(saved) => MutationSupport.retry(saved, digest)
        case None =>
          for
            state <- MutationSupport.stateForNewRequest(connection, session, expectedSequence)
            changes <- IO.fromEither(FixtureRules.open(session.fixture, state, operation))
            result <- MutationSupport.commit(connection, session, requestId, digest, changes)
          yield result
    yield ExecuteOperationResponse(result)

object ExecuteOperationMessage:
  given Encoder[ExecuteOperationMessage] = deriveEncoder[ExecuteOperationMessage]
  given Decoder[ExecuteOperationMessage] = deriveDecoder[ExecuteOperationMessage]
