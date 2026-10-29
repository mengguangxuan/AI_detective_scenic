package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*

final case class PoliceDeskView(point: PointId, name: String, background: String,
  sharedClues: Set[ClueId], exchanges: Vector[PoliceExchangeView])
object PoliceDeskView:
  given Encoder[PoliceDeskView] = deriveEncoder[PoliceDeskView]
  given Decoder[PoliceDeskView] = deriveDecoder[PoliceDeskView]
