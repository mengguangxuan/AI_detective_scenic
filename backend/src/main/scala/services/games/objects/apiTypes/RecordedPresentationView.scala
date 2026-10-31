package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.ClueId
import services.system.objects.Sequence

final case class RecordedPresentationView(sequence: Sequence, evidence: Set[ClueId], reply: String)
object RecordedPresentationView:
  given Encoder[RecordedPresentationView] = deriveEncoder[RecordedPresentationView]
  given Decoder[RecordedPresentationView] = deriveDecoder[RecordedPresentationView]
