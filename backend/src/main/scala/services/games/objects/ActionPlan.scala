package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.games.objects.apiTypes.CommandResult


final case class ActionPlan(session: GameSessionRecord, record: SessionRecord, result: CommandResult)

object ActionPlan:
  given Encoder[ActionPlan] = deriveEncoder[ActionPlan]
  given Decoder[ActionPlan] = deriveDecoder[ActionPlan]
