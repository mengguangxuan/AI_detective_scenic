package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.{ClueId, WitnessAnswerId}

final case class InterviewStatementUpdate(answerId: WitnessAnswerId, evidenceIds: Set[ClueId], text: String)
object InterviewStatementUpdate:
  given Encoder[InterviewStatementUpdate] = deriveEncoder[InterviewStatementUpdate]
  given Decoder[InterviewStatementUpdate] = deriveDecoder[InterviewStatementUpdate]
