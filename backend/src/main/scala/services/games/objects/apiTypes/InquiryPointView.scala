package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class InquiryPointView(id: PointId, label: String, operations: Vector[OperationChoiceView], routes: Vector[RouteChoiceView], findings: Vector[ClueView],
  presentations: Vector[RecordedPresentationView] = Vector.empty)
object InquiryPointView:
  given Encoder[InquiryPointView] = deriveEncoder[InquiryPointView].mapJson(json =>
    if json.hcursor.get[Vector[RecordedPresentationView]]("presentations").contains(Vector.empty) then json.mapObject(_.remove("presentations")) else json)
  given Decoder[InquiryPointView] = deriveDecoder[InquiryPointView].prepare(_.withFocus(_.mapObject(fields =>
    if fields.contains("presentations") then fields else fields.add("presentations",io.circe.Json.arr()))))
