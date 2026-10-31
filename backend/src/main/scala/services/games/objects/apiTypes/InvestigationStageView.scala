package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

final case class InvestigationStageView(introduction: String, objective: String, completed: Boolean, nextObjective: Option[String] = None,
  hasTimeComparison: Option[Boolean] = None)
object InvestigationStageView:
  given Encoder[InvestigationStageView] = deriveEncoder[InvestigationStageView].mapJson(json =>
    if json.hcursor.downField("hasTimeComparison").focus.contains(io.circe.Json.Null) then json.mapObject(_.remove("hasTimeComparison")) else json)
  given Decoder[InvestigationStageView] = deriveDecoder[InvestigationStageView]
