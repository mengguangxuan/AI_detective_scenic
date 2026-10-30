package services.cases.objects.scene
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.SceneNodeId
final case class SceneObservation(node: SceneNodeId, label: String, description: String)
object SceneObservation:
  given Encoder[SceneObservation] = deriveEncoder[SceneObservation]
  given Decoder[SceneObservation] = deriveDecoder[SceneObservation]
