package services.games.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*
import services.cases.objects.content.{FixtureClue,CasePersonBrief}
import services.cases.objects.investigation.ReasoningEvaluationSpec
import services.games.objects.apiTypes.CommandResult
import services.system.objects.*

enum ReasoningRequestStatus:
  case Pending, Finished, Cancelled
object ReasoningRequestStatus:
  given Encoder[ReasoningRequestStatus] = Encoder.encodeString.contramap(_.toString)
  given Decoder[ReasoningRequestStatus] = Decoder.decodeString.emap(s => ReasoningRequestStatus.values.find(_.toString == s).toRight("Unknown reasoning request status"))

final case class ReasoningContext(caseId: CaseId, revision: Revision, evaluator: ReasoningEvaluationSpec,
  culprits: Set[PersonId], people: Vector[CasePersonBrief], evidence: Vector[FixtureClue], history: Vector[ReasoningAttempt], obtainableEvidence: Vector[FixtureClue])
object ReasoningContext:
  given Encoder[ReasoningContext] = deriveEncoder[ReasoningContext]
  given Decoder[ReasoningContext] = deriveDecoder[ReasoningContext]

final case class ReasoningRequestRecord(sessionId: SessionId, requestId: RequestId, digest: String, expectedSequence: Sequence,
  suspects: Set[PersonId], evidence: Set[ClueId], explanation: String, context: ReasoningContext,
  leaseToken: String, leaseUntilMillis: Long, status: ReasoningRequestStatus)
object ReasoningRequestRecord:
  given Encoder[ReasoningRequestRecord] = deriveEncoder[ReasoningRequestRecord]
  given Decoder[ReasoningRequestRecord] = deriveDecoder[ReasoningRequestRecord]

enum ReasoningPreparation:
  case Cached(result: CommandResult)
  case Reserved(request: ReasoningRequestRecord)
  case Rejected(error: GameError)

final case class ReasoningDecision(checks: Vector[ReasoningCheck], evidence: Set[ClueId])
object ReasoningDecision:
  given Encoder[ReasoningDecision] = deriveEncoder[ReasoningDecision]
  given Decoder[ReasoningDecision] = deriveDecoder[ReasoningDecision]
