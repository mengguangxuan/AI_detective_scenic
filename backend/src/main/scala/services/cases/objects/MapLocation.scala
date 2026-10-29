package services.cases.objects
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class MapLocation(id: PlaceId, name: String, description: String, x: Double, y: Double)
object MapLocation:
  given Encoder[MapLocation] = deriveEncoder[MapLocation]
  given Decoder[MapLocation] = deriveDecoder[MapLocation]
