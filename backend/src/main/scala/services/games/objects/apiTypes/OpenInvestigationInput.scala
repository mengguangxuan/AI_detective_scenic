package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.CaseId
import services.system.objects.{SessionId}

final case class OpenInvestigationInput(sessionId: SessionId, caseId: CaseId)
object OpenInvestigationInput:
  given Encoder[OpenInvestigationInput] = deriveEncoder[OpenInvestigationInput]
  given Decoder[OpenInvestigationInput] = deriveDecoder[OpenInvestigationInput]
