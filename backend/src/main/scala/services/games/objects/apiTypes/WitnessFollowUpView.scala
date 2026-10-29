package services.games.objects.apiTypes
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*
final case class WitnessFollowUpView(operation: OperationId, witness: WitnessId, title: String, started: Boolean, ready: Boolean, requirements: Vector[FollowUpRequirementView], questions: Vector[RouteChoiceView])
object WitnessFollowUpView:
  given Encoder[WitnessFollowUpView] = deriveEncoder[WitnessFollowUpView]
  given Decoder[WitnessFollowUpView] = deriveDecoder[WitnessFollowUpView]
