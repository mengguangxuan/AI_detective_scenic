package services.games.api

import cats.effect.IO
import java.sql.Connection
import services.cases.objects.CaseId
import services.cases.tables.CaseReleaseTable.CaseReleaseTable
import services.cases.tables.CaseTable.CaseTable
import services.games.objects.apiTypes.InvestigationSnapshot
import services.games.tables.SessionTable.SessionTable
import services.games.utils.{MutationSupport, InvestigationProjection}
import services.system.api.APIMessage
import services.system.objects.*

final case class OpenInvestigationMessage(userId: UserId,sessionId: SessionId,caseId: CaseId) extends APIMessage[InvestigationSnapshot]:
  override def plan(connection: Connection): IO[InvestigationSnapshot] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit))
      _ <- IO.raiseUnless(sessionId.value.matches("[a-f0-9-]{36}"))(GameError(ErrorCode.InvalidValue,"会话编号格式不正确。"))
      _ <- CaseTable.lock(connection,caseId).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.AccessDenied,"该案件暂未开放。")))
      release <- CaseReleaseTable.find(connection,caseId).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.AccessDenied,"该案件暂未发布。")))
      _ <- IO.raiseUnless(InvestigationProjection.supportsScene(release.sceneBundle))(GameError(ErrorCode.AccessDenied,"该案件需要的场景暂不支持。"))
      existing <- SessionTable.lock(connection,sessionId)
      _ <- existing match
        case Some(row) => MutationSupport.owned(Some(row),userId).flatMap(saved =>
          IO.raiseUnless(saved.caseId == caseId)(GameError(ErrorCode.RequestConflict,"原开局请求绑定了另一案件，请保留原请求。")))
        case None => StartGameMessage(userId,sessionId,caseId).plan(connection).void
      result <- GetInvestigationSnapshotMessage(userId,sessionId).plan(connection)
    yield result
