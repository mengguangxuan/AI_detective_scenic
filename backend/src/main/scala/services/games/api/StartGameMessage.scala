package services.games.api

import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.cases.objects.InvestigationFixture
import services.games.objects.StoredSession
import services.games.objects.apiTypes.StartGameResponse
import services.games.tables.SessionTable.SessionTable
import services.games.utils.FixtureRules
import services.system.api.APIMessage
import services.system.objects.*

// Trusted internal caller supplies the authoring-validated fixture; no HTTP registration.
final case class StartGameMessage(userId: UserId, sessionId: SessionId, fixture: InvestigationFixture)
    extends APIMessage[StartGameResponse]:
  override def plan(connection: Connection): IO[StartGameResponse] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit, "Message requires transaction"))
      _ <- SessionTable.insert(connection, StoredSession(sessionId, userId, fixture, FixtureRules.initial(fixture), Sequence(0)))
    yield StartGameResponse(sessionId, Sequence(0))

object StartGameMessage:
  given Encoder[StartGameMessage] = deriveEncoder[StartGameMessage]
  given Decoder[StartGameMessage] = deriveDecoder[StartGameMessage]
