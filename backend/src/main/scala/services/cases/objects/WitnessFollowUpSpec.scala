package services.cases.objects
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class WitnessFollowUpSpec(operation: OperationId, witness: WitnessId, title: String, requiredEvidence: Set[ClueId], requiredRoutes: Set[RouteId], knowledge: Vector[String], policy: Vector[String])
object WitnessFollowUpSpec:
  given Encoder[WitnessFollowUpSpec] = deriveEncoder[WitnessFollowUpSpec]
  given Decoder[WitnessFollowUpSpec] = deriveDecoder[WitnessFollowUpSpec]
