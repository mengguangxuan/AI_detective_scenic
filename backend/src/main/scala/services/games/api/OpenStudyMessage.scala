package services.games.api
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import cats.effect.IO
import java.sql.Connection
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.system.api.APIMessage
import services.system.objects.*
import services.games.objects.apiTypes.InvestigationSnapshot
import services.games.tables.SessionTable.SessionTable
import services.games.utils.{MutationSupport,StudyProjection}
// A browser-chosen session UUID makes initial creation retryable; the published case is chosen here.
final case class OpenStudyMessage(userId: UserId,sessionId: SessionId) extends APIMessage[InvestigationSnapshot]:
  override def plan(connection: Connection): IO[InvestigationSnapshot] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit))
      _ <- IO.raiseUnless(sessionId.value.matches("[a-f0-9-]{36}"))(GameError(ErrorCode.InvalidValue,"会话编号格式不正确。"))
      _ <- CaseRevisionTable.lock(connection,StudyProjection.caseId,StudyProjection.revision).flatMap(value =>
        IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"公开示例尚未初始化。")))
      existing <- SessionTable.lock(connection,sessionId)
      _ <- existing match
        case Some(row) => MutationSupport.owned(Some(row),userId).flatMap(saved =>
          IO.raiseUnless(saved.caseId == StudyProjection.caseId && saved.caseRevision == StudyProjection.revision)(GameError(ErrorCode.ConflictingContent,"会话绑定了不同案件。")))
        case None => StartGameMessage(userId,sessionId,StudyProjection.caseId,StudyProjection.revision).plan(connection).void
      result <- GetInvestigationSnapshotMessage(userId,sessionId).plan(connection)
    yield result

object OpenStudyMessage:
  given Encoder[OpenStudyMessage] = deriveEncoder[OpenStudyMessage]
  given Decoder[OpenStudyMessage] = deriveDecoder[OpenStudyMessage]
