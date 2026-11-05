package services.sceneproduction.api
import cats.effect.IO
import java.sql.Connection
import services.sceneproduction.objects.*
import services.sceneproduction.tables.SceneProductionTable.SceneProductionTable
import services.system.api.APIMessage
import services.system.objects.UserId
final case class ListSceneProductionsMessage(userId: UserId) extends APIMessage[Vector[SceneProductionRecord]]:
  def plan(c: Connection): IO[Vector[SceneProductionRecord]] = SceneProductionTable.list(c,userId)
