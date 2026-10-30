package services.cases.objects.investigation
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.ClueId

final case class StatementComparisonRowSpec(label: String, initial: ClueId, revised: ClueId)
object StatementComparisonRowSpec:
  given Encoder[StatementComparisonRowSpec] = deriveEncoder[StatementComparisonRowSpec]
  given Decoder[StatementComparisonRowSpec] = deriveDecoder[StatementComparisonRowSpec]
