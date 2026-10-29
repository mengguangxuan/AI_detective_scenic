package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.ClueId
import services.system.objects.*

final case class ReportCluesInput(sessionId: SessionId, requestId: RequestId, expectedSequence: Sequence, evidence: Set[ClueId])
object ReportCluesInput:
  given Encoder[ReportCluesInput] = deriveEncoder[ReportCluesInput]
  given Decoder[ReportCluesInput] = deriveDecoder[ReportCluesInput]
