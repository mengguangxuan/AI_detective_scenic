package services.games.objects.apiTypes
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.ExternalCheckId
final case class ExternalCheckView(id: ExternalCheckId, title: String, requestLabel: String, delayMinutes: Int, available: Boolean, progress: Option[CheckProgressView])
object ExternalCheckView:
  given Encoder[ExternalCheckView] = deriveEncoder[ExternalCheckView]
  given Decoder[ExternalCheckView] = deriveDecoder[ExternalCheckView]
