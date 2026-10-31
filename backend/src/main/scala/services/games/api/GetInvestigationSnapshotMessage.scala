package services.games.api
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import cats.effect.IO
import java.sql.Connection
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.cases.tables.CaseReleaseTable.CaseReleaseTable
import services.games.objects.apiTypes.InvestigationSnapshot
import services.games.tables.SessionTable.SessionTable
import services.games.tables.SessionRecordTable.SessionRecordTable
import services.games.tables.InterviewTurnTable.InterviewTurnTable
import services.games.tables.PoliceExchangeTable.PoliceExchangeTable
import services.games.utils.{MutationSupport,StudyProjection}
import services.system.api.APIMessage
import services.system.objects.*
final case class GetInvestigationSnapshotMessage(userId: UserId,sessionId: SessionId) extends APIMessage[InvestigationSnapshot]:
  override def plan(connection: Connection): IO[InvestigationSnapshot] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit))
      state <- SessionTable.lock(connection,sessionId).flatMap(MutationSupport.owned(_,userId))
      revision <- CaseRevisionTable.find(connection,state.caseId,state.caseRevision).flatMap(v => IO.fromOption(v)(GameError(ErrorCode.InvalidValue,"案件修订不存在。")))
      records <- SessionRecordTable.list(connection,state.id)
      turns <- InterviewTurnTable.list(connection,state.id)
      exchanges <- PoliceExchangeTable.list(connection,state.id)
      release <- CaseReleaseTable.find(connection,state.caseId,state.caseRevision)
    yield StudyProjection.snapshot(revision,state,records,turns,exchanges,release)

object GetInvestigationSnapshotMessage:
  given Encoder[GetInvestigationSnapshotMessage] = deriveEncoder[GetInvestigationSnapshotMessage]
  given Decoder[GetInvestigationSnapshotMessage] = deriveDecoder[GetInvestigationSnapshotMessage]
