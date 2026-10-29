package services.games.objects.apiTypes
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class CheckProgressView(requestedAt: String, readyAt: String, completedAt: Option[String])
object CheckProgressView:
  given Encoder[CheckProgressView] = deriveEncoder[CheckProgressView]
  given Decoder[CheckProgressView] = deriveDecoder[CheckProgressView]
