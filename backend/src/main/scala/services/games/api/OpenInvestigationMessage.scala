package services.games.api

import cats.effect.IO
import java.sql.Connection
import services.cases.objects.CaseId
import services.cases.tables.CaseReleaseTable.CaseReleaseTable
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.objects.apiTypes.InvestigationSnapshot
import services.games.tables.SessionTable.SessionTable
import services.games.utils.{MutationSupport, StudyProjection}
import services.system.api.APIMessage
import services.system.objects.*

final case class OpenInvestigationMessage(userId: UserId,sessionId: SessionId,caseId: CaseId,revision: Revision) extends APIMessage[InvestigationSnapshot]:
  override def plan(connection: Connection): IO[InvestigationSnapshot] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit))
      _ <- IO.raiseUnless(sessionId.value.matches("[a-f0-9-]{36}"))(GameError(ErrorCode.InvalidValue,"会话编号格式不正确。"))
      _ <- CaseRevisionTable.lock(connection,caseId,revision).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.AccessDenied,"该案件暂未开放。")))
      release <- CaseReleaseTable.find(connection,caseId,revision).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.AccessDenied,"该修订暂未发布。")))
      _ <- IO.raiseUnless(StudyProjection.supportsScene(release.sceneBundle,release.sceneRevision))(GameError(ErrorCode.AccessDenied,"该案件需要的场景暂不支持。"))
      existing <- SessionTable.lock(connection,sessionId)
      _ <- existing match
        case Some(row) => MutationSupport.owned(Some(row),userId).flatMap(saved =>
          IO.raiseUnless(saved.caseId == caseId && saved.caseRevision == revision)(GameError(ErrorCode.RequestConflict,"原开局请求绑定了另一案件或修订，请保留原请求。")))
        case None => StartGameMessage(userId,sessionId,caseId,revision).plan(connection).void
      result <- GetInvestigationSnapshotMessage(userId,sessionId).plan(connection)
    yield result
