package services.cases.objects
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class StatementComparisonRowSpec(label: String, initial: ClueId, revised: ClueId)
object StatementComparisonRowSpec:
  given Encoder[StatementComparisonRowSpec] = deriveEncoder[StatementComparisonRowSpec]
  given Decoder[StatementComparisonRowSpec] = deriveDecoder[StatementComparisonRowSpec]
