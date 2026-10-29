package services.games.objects.apiTypes
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.WitnessId
final case class StatementComparisonView(witness: WitnessId, title: String, rows: Vector[StatementComparisonRowView], nextObjective: Option[String])
object StatementComparisonView:
  given Encoder[StatementComparisonView] = deriveEncoder[StatementComparisonView]
  given Decoder[StatementComparisonView] = deriveDecoder[StatementComparisonView]
