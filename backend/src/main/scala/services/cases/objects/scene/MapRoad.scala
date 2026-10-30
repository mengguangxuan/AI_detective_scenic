package services.cases.objects.scene
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.PlaceId

final case class MapRoad(id: String, from: PlaceId, to: PlaceId, meters: Int, label: String)
object MapRoad:
  given Encoder[MapRoad] = deriveEncoder[MapRoad]
  given Decoder[MapRoad] = deriveDecoder[MapRoad]
