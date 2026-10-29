package services.games.objects.apiTypes
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class StatementComparisonRowView(label: String, initial: Option[StatementVersionView], revised: Option[StatementVersionView])
object StatementComparisonRowView:
  given Encoder[StatementComparisonRowView] = deriveEncoder[StatementComparisonRowView]
  given Decoder[StatementComparisonRowView] = deriveDecoder[StatementComparisonRowView]
