package services.cases.objects.investigation
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.WitnessId

final case class StatementComparisonSpec(witness: WitnessId, title: String, rows: Vector[StatementComparisonRowSpec], nextObjective: String)
object StatementComparisonSpec:
  given Encoder[StatementComparisonSpec] = deriveEncoder[StatementComparisonSpec]
  given Decoder[StatementComparisonSpec] = deriveDecoder[StatementComparisonSpec]
