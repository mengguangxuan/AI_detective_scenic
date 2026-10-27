package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class ClueView(id: ClueId, title: String, text: String)
object ClueView:
  given Encoder[ClueView] = deriveEncoder[ClueView]
  given Decoder[ClueView] = deriveDecoder[ClueView]
