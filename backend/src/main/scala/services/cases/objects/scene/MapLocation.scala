package services.cases.objects.scene
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.PlaceId

final case class MapLocation(id: PlaceId, name: String, description: String, x: Double, y: Double)
object MapLocation:
  given Encoder[MapLocation] = deriveEncoder[MapLocation]
  given Decoder[MapLocation] = deriveDecoder[MapLocation]
