package services.cases.objects.scene
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class AreaMap(note: String, locations: Vector[MapLocation], roads: Vector[MapRoad])
object AreaMap:
  given Encoder[AreaMap] = deriveEncoder[AreaMap]
  given Decoder[AreaMap] = deriveDecoder[AreaMap]
