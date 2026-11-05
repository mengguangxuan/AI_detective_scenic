package services.sceneproduction.api
import cats.effect.IO
import java.sql.Connection
import services.sceneproduction.objects.*
import services.system.api.APIMessage
import services.system.objects.UserId
final case class ConfirmSceneLayoutMessage(userId: UserId,productionId: SceneProductionId) extends APIMessage[SceneProductionRecord]:
  def plan(c: Connection): IO[SceneProductionRecord] = GetSceneProductionMessage(userId,productionId).plan(c)
