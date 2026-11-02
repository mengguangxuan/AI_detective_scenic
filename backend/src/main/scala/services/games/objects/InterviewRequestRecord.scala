package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class InterviewRequestRecord(sessionId: SessionId, requestId: RequestId, digest: String, expectedSequence: Sequence, witness: WitnessId, question: String, shownClues: Set[ClueId], context: InterviewContext, leaseToken: String, leaseUntilMillis: Long, status: InterviewRequestStatus, draft: Option[InterviewDecision] = None)
object InterviewRequestRecord:
  given Encoder[InterviewRequestRecord] = deriveEncoder[InterviewRequestRecord].mapJson(json => if json.hcursor.get[Option[InterviewDecision]]("draft").contains(None) then json.mapObject(_.remove("draft")) else json)
  given Decoder[InterviewRequestRecord] = deriveDecoder[InterviewRequestRecord]
