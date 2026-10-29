package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.ClueId
import services.system.objects.*

final case class PoliceExchangeView(requestId: RequestId, sequence: Sequence, evidence: Set[ClueId], reply: String)
object PoliceExchangeView:
  given Encoder[PoliceExchangeView] = deriveEncoder[PoliceExchangeView]
  given Decoder[PoliceExchangeView] = deriveDecoder[PoliceExchangeView]
