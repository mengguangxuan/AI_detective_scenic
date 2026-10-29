package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

final case class InvestigationStageView(introduction: String, objective: String, completed: Boolean, nextObjective: Option[String] = None)
object InvestigationStageView:
  given Encoder[InvestigationStageView] = deriveEncoder[InvestigationStageView]
  given Decoder[InvestigationStageView] = deriveDecoder[InvestigationStageView]
