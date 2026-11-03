package services.games.objects

import io.circe.{Decoder, Encoder}

// Finite actions executed by the investigation service, independent of any contact or scene point.
enum InvestigationSystemAction:
  case Wait, EvaluateReasoning, SubmitConclusion, RevealReasoning
object InvestigationSystemAction:
  given Encoder[InvestigationSystemAction] = Encoder.encodeString.contramap(_.toString)
  given Decoder[InvestigationSystemAction] = Decoder.decodeString.emap(value =>
    InvestigationSystemAction.values.find(_.toString == value).toRight("Unknown investigation system action"))
