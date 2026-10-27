package services.cases.objects
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
final case class SceneObservation(node: SceneNodeId, label: String, description: String)
object SceneObservation:
  given Encoder[SceneObservation] = deriveEncoder[SceneObservation]
  given Decoder[SceneObservation] = deriveDecoder[SceneObservation]
