package services.cases.objects
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class MapRoad(id: String, from: PlaceId, to: PlaceId, meters: Int, label: String)
object MapRoad:
  given Encoder[MapRoad] = deriveEncoder[MapRoad]
  given Decoder[MapRoad] = deriveDecoder[MapRoad]
