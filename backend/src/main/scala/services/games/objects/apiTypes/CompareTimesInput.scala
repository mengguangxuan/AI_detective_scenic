package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class CompareTimesInput(sessionId: SessionId, requestId: RequestId, expectedSequence: Sequence,
  evidence: Set[ClueId], convertedTimeMinutes: Int, assessment: TimeAssessment)
object CompareTimesInput:
  given Encoder[CompareTimesInput] = deriveEncoder[CompareTimesInput]
  given Decoder[CompareTimesInput] = deriveDecoder[CompareTimesInput]
