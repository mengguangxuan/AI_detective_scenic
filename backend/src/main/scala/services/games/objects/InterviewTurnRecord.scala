package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class InterviewTurnRecord(sessionId: SessionId, requestId: RequestId, sequence: Sequence, witness: WitnessId, question: String, shownClues: Set[ClueId], reply: String, mode: InterviewReplyMode, answerIds: Vector[WitnessAnswerId])
object InterviewTurnRecord:
  given Encoder[InterviewTurnRecord] = deriveEncoder[InterviewTurnRecord]
  given Decoder[InterviewTurnRecord] = deriveDecoder[InterviewTurnRecord]
