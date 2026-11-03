package services.games.api

import cats.effect.IO
import java.sql.Connection
import services.cases.objects.CaseReleaseRecord
import services.cases.tables.CaseReleaseTable.CaseReleaseTable
import services.cases.tables.CaseTable.CaseTable
import services.games.utils.InvestigationProjection
import services.system.api.APIMessage
import services.system.objects.*

// Internal publication entry; it is deliberately absent from public HTTP routes.
final case class RegisterPlayableCaseMessage(release: CaseReleaseRecord) extends APIMessage[Unit]:
  override def plan(connection: Connection): IO[Unit] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit))
      caseRecord <- CaseTable.lock(connection,release.caseId).flatMap(value =>
        IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"待发布案件不存在。")))
      _ <- IO.raiseUnless(release.summary.trim.nonEmpty && release.summary.length <= 2000 && release.tags.size <= 8 &&
        release.tags.forall(t => t.trim.nonEmpty && t.length <= 40) && InvestigationProjection.supportsScene(release.sceneBundle) && release.sceneBundle == caseRecord.content.sceneBundle &&
        caseRecord.content.sceneObjects.nonEmpty)(GameError(ErrorCode.InvalidValue,"案件介绍或场景能力不受支持。"))
      existing <- CaseReleaseTable.find(connection,release.caseId)
      _ <- existing match
        case Some(saved) => IO.raiseUnless(saved == release)(GameError(ErrorCode.ConflictingContent,"已发布案件的介绍和场景绑定不能覆盖。"))
        case None => CaseReleaseTable.insert(connection,release)
    yield ()
