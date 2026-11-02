package services.games.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.ClueId

final case class InterviewClaimOption(clue: ClueId, meaning: String, revised: Boolean, priorStatement: Option[ClueId], basisInstruction: String)
object InterviewClaimOption:
  given Encoder[InterviewClaimOption] = deriveEncoder[InterviewClaimOption]
  given Decoder[InterviewClaimOption] = deriveDecoder[InterviewClaimOption]
