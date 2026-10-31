package services.games.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.ClueId

// Copy the actually acquired material at the ending, so later investigation cannot rewrite it.
final case class ReviewMaterial(id: ClueId, title: String, text: String, cited: Boolean)
object ReviewMaterial:
  given Encoder[ReviewMaterial] = deriveEncoder[ReviewMaterial]
  given Decoder[ReviewMaterial] = deriveDecoder[ReviewMaterial]
