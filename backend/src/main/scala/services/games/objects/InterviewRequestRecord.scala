package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class InterviewRequestRecord(sessionId: SessionId, requestId: RequestId, digest: String, expectedSequence: Sequence, witness: WitnessId, question: String, shownClues: Set[ClueId], context: InterviewContext, leaseToken: String, leaseUntilMillis: Long, status: InterviewRequestStatus)
object InterviewRequestRecord:
  given Encoder[InterviewRequestRecord] = deriveEncoder[InterviewRequestRecord]
  given Decoder[InterviewRequestRecord] = deriveDecoder[InterviewRequestRecord]
