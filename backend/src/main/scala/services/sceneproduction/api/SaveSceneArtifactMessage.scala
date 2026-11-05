package services.sceneproduction.api
import cats.effect.IO
import java.sql.Connection
import java.time.Instant
import services.sceneproduction.objects.*
import services.sceneproduction.tables.SceneProductionTable.SceneProductionTable
import services.sceneproduction.utils.SceneProductionValidation
import services.system.api.APIMessage
import services.system.objects.*
final case class SaveSceneArtifactMessage(userId: UserId,productionId: SceneProductionId) extends APIMessage[SceneProductionRecord]:
  def plan(c: Connection): IO[SceneProductionRecord] = for
    row <- SceneProductionTable.lock(c,productionId).flatMap(SceneProductionValidation.owned(_,userId))
    result <- if row.phase==SceneProductionPhase.Saved then IO.pure(row)
      else if row.phase!=SceneProductionPhase.Queued then IO.raiseError(GameError(ErrorCode.ConditionFailed,"请先完成场景制作与审核。"))
      else
        val saved=Instant.now().toString
        SceneProductionTable.markSaved(c,productionId,saved).as(row.copy(phase=SceneProductionPhase.Saved,savedAt=Some(saved)))
  yield result
