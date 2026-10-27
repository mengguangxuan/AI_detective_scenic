package services.games.api

import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.games.objects.apiTypes.GetGameStateResponse
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.tables.SessionTable.SessionTable
import services.games.utils.{FixtureRules, MutationSupport}
import services.system.api.APIMessage
import services.system.objects.*

final case class GetGameStateMessage(userId: UserId, sessionId: SessionId) extends APIMessage[GetGameStateResponse]:
  override def plan(connection: Connection): IO[GetGameStateResponse] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit, "Message requires transaction"))
      row <- SessionTable.find(connection,sessionId).flatMap(MutationSupport.owned(_,userId))
      revision <- CaseRevisionTable.find(connection,row.caseId,row.caseRevision).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件修订不存在。")))
    yield FixtureRules.view(revision.content,row)

object GetGameStateMessage:
  given Encoder[GetGameStateMessage] = deriveEncoder[GetGameStateMessage]
  given Decoder[GetGameStateMessage] = deriveDecoder[GetGameStateMessage]
