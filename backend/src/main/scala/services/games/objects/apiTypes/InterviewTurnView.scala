package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.games.objects.InterviewReplyMode
import services.system.objects.*
import services.cases.objects.{ClueId,WitnessId}

final case class InterviewTurnView(requestId: RequestId, sequence: Sequence, witness: WitnessId, question: String, shownClues: Set[ClueId], reply: String, mode: InterviewReplyMode)
object InterviewTurnView:
  given Encoder[InterviewTurnView] = deriveEncoder[InterviewTurnView]
  given Decoder[InterviewTurnView] = deriveDecoder[InterviewTurnView]
