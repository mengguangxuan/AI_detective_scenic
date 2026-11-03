package services.cases.objects.scene

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.PlaceId
import services.system.objects.Vec3

// Registered renderer and finite, authored walking stops for a small exploration scene.
final case class SceneLocation(place: PlaceId, name: String, bundleId: String, stops: Vector[Vec3])
object SceneLocation:
  given Encoder[SceneLocation] = deriveEncoder[SceneLocation]
  given Decoder[SceneLocation] = deriveDecoder[SceneLocation]
