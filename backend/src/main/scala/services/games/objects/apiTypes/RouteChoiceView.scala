package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class RouteChoiceView(id: RouteId, label: String)
object RouteChoiceView:
  given Encoder[RouteChoiceView] = deriveEncoder[RouteChoiceView]
  given Decoder[RouteChoiceView] = deriveDecoder[RouteChoiceView]
