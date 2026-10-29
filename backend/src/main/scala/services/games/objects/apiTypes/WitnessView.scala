package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.{PointId,WitnessId}

final case class WitnessView(id: WitnessId, point: PointId, name: String, background: String)
object WitnessView:
  given Encoder[WitnessView] = deriveEncoder[WitnessView]
  given Decoder[WitnessView] = deriveDecoder[WitnessView]
