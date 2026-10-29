package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.ClueId
import services.system.objects.*

final case class PoliceExchangeRecord(sessionId: SessionId, requestId: RequestId, sequence: Sequence,
  evidence: Set[ClueId], reply: String)
object PoliceExchangeRecord:
  given Encoder[PoliceExchangeRecord] = deriveEncoder[PoliceExchangeRecord]
  given Decoder[PoliceExchangeRecord] = deriveDecoder[PoliceExchangeRecord]
