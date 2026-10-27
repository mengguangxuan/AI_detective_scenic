package services.games.api

import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.cases.objects.{PointId, RouteId}
import services.games.objects.FixtureCommand
import services.games.objects.apiTypes.InvestigateResponse
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.utils.{FixtureRules, MutationSupport}
import services.system.api.APIMessage
import services.system.objects.*

final case class InvestigateMessage(userId: UserId, sessionId: SessionId,
    requestId: RequestId, expectedSequence: Sequence, point: PointId, route: RouteId)
    extends APIMessage[InvestigateResponse]:
  override def plan(connection: Connection): IO[InvestigateResponse] =
    val digest = MutationSupport.digest(userId, sessionId, requestId, expectedSequence, FixtureCommand.Investigate(point, route))
    for
      _ <- IO.blocking(require(!connection.getAutoCommit, "Message requires transaction"))
      session <- MutationSupport.lockOwned(connection, userId, sessionId)
      receipt <- ReceiptTable.find(connection, sessionId, requestId)
      result <- receipt match
        case Some(saved) => MutationSupport.retry(saved, digest)
        case None =>
          for
            state <- MutationSupport.stateForNewRequest(connection, session, expectedSequence)
            changes <- IO.fromEither(FixtureRules.investigate(session.fixture, state, requestId, point, route))
            result <- MutationSupport.commit(connection, session, requestId, digest, changes)
          yield result
    yield InvestigateResponse(result)

object InvestigateMessage:
  given Encoder[InvestigateMessage] = deriveEncoder[InvestigateMessage]
  given Decoder[InvestigateMessage] = deriveDecoder[InvestigateMessage]
