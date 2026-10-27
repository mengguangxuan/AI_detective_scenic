package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class OperationChoiceView(id: OperationId, label: String)
object OperationChoiceView:
  given Encoder[OperationChoiceView] = deriveEncoder[OperationChoiceView]
  given Decoder[OperationChoiceView] = deriveDecoder[OperationChoiceView]
