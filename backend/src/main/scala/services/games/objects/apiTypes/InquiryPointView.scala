package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class InquiryPointView(id: PointId, label: String, operations: Vector[OperationChoiceView], routes: Vector[RouteChoiceView])
object InquiryPointView:
  given Encoder[InquiryPointView] = deriveEncoder[InquiryPointView]
  given Decoder[InquiryPointView] = deriveDecoder[InquiryPointView]
