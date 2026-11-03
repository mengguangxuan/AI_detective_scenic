package services.games.api

import cats.effect.IO
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseTable.CaseTable
import services.games.objects.apiTypes.StartGameResponse
import services.games.tables.SessionTable.SessionTable
import services.games.utils.FixtureRules
import services.system.api.APIMessage
import services.system.objects.*

final case class StartGameMessage(userId: UserId,sessionId: SessionId,caseId: CaseId) extends APIMessage[StartGameResponse]:
  override def plan(connection: Connection): IO[StartGameResponse] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit,"Message requires transaction"))
      record <- CaseTable.find(connection,caseId).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件不存在。")))
      _ <- SessionTable.insert(connection,FixtureRules.initial(record,sessionId,userId))
    yield StartGameResponse(sessionId,Sequence(0))

object StartGameMessage:
  given Encoder[StartGameMessage] = deriveEncoder[StartGameMessage]
  given Decoder[StartGameMessage] = deriveDecoder[StartGameMessage]
