package services.games.api

import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.games.objects.apiTypes.GetGameStateResponse
import services.games.tables.CommitTable.CommitTable
import services.games.utils.{FixtureRules, MutationSupport}
import services.system.api.APIMessage
import services.system.objects.*

final case class GetGameStateMessage(userId: UserId, sessionId: SessionId) extends APIMessage[GetGameStateResponse]:
  override def plan(connection: Connection): IO[GetGameStateResponse] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit, "Message requires transaction"))
      row <- MutationSupport.lockOwned(connection, userId, sessionId)
      log <- CommitTable.list(connection, sessionId)
      state <- IO.fromEither(FixtureRules.replay(row, log))
    yield FixtureRules.view(row.fixture, state)

object GetGameStateMessage:
  given Encoder[GetGameStateMessage] = deriveEncoder[GetGameStateMessage]
  given Decoder[GetGameStateMessage] = deriveDecoder[GetGameStateMessage]
