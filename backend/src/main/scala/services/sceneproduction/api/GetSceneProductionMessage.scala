package services.sceneproduction.api
import cats.effect.IO
import java.sql.Connection
import services.sceneproduction.objects.*
import services.sceneproduction.tables.SceneProductionTable.SceneProductionTable
import services.sceneproduction.utils.SceneProductionValidation
import services.system.api.APIMessage
import services.system.objects.UserId
final case class GetSceneProductionMessage(userId: UserId,productionId: SceneProductionId) extends APIMessage[SceneProductionRecord]:
  def plan(c: Connection): IO[SceneProductionRecord] = SceneProductionTable.find(c,productionId).flatMap(SceneProductionValidation.owned(_,userId))
