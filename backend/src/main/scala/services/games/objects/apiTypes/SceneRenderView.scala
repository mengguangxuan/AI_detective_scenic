package services.games.objects.apiTypes
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.SceneNodeId
import services.cases.objects.scene.{SceneRoom,ScenePrimitive}
import services.system.objects.Vec3
final case class SceneInstanceView(node: SceneNodeId, position: Vec3, parts: Vector[ScenePrimitive])
object SceneInstanceView:
  given Encoder[SceneInstanceView] = deriveEncoder[SceneInstanceView]
  given Decoder[SceneInstanceView] = deriveDecoder[SceneInstanceView]
final case class SceneRenderView(room: SceneRoom, instances: Vector[SceneInstanceView])
object SceneRenderView:
  given Encoder[SceneRenderView] = deriveEncoder[SceneRenderView]
  given Decoder[SceneRenderView] = deriveDecoder[SceneRenderView]
