package services.games.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*
import services.system.objects.*

// Finite outcomes drive retry and ending controls; narrative criteria remain author data.
enum ReasoningStatus:
  case Revise, Established, Unavailable, Rejected
object ReasoningStatus:
  given Encoder[ReasoningStatus] = Encoder.encodeString.contramap(_.toString)
  given Decoder[ReasoningStatus] = Decoder.decodeString.emap(s => ReasoningStatus.values.find(_.toString == s).toRight("Unknown reasoning status"))

final case class ReasoningCheck(id: String, label: String, satisfied: Boolean)
object ReasoningCheck:
  given Encoder[ReasoningCheck] = deriveEncoder[ReasoningCheck]
  given Decoder[ReasoningCheck] = deriveDecoder[ReasoningCheck]

final case class ReasoningAttempt(sequence: Sequence, requestId: RequestId, suspects: Set[PersonId], evidence: Set[ClueId],
  explanation: String, status: ReasoningStatus, checks: Vector[ReasoningCheck], feedback: String)
object ReasoningAttempt:
  given Encoder[ReasoningAttempt] = deriveEncoder[ReasoningAttempt]
  given Decoder[ReasoningAttempt] = deriveDecoder[ReasoningAttempt]

enum ReasoningEndingMode:
  case Solved, Revealed
object ReasoningEndingMode:
  given Encoder[ReasoningEndingMode] = Encoder.encodeString.contramap(_.toString)
  given Decoder[ReasoningEndingMode] = Decoder.decodeString.emap(s => ReasoningEndingMode.values.find(_.toString == s).toRight("Unknown reasoning ending"))

final case class ReasoningEnding(sequence: Sequence, mode: ReasoningEndingMode, review: String)
object ReasoningEnding:
  given Encoder[ReasoningEnding] = deriveEncoder[ReasoningEnding]
  given Decoder[ReasoningEnding] = deriveDecoder[ReasoningEnding]

final case class ReasoningProgress(attempts: Vector[ReasoningAttempt], ending: Option[ReasoningEnding])
object ReasoningProgress:
  val empty = ReasoningProgress(Vector.empty,None)
  given Encoder[ReasoningProgress] = deriveEncoder[ReasoningProgress]
  given Decoder[ReasoningProgress] = deriveDecoder[ReasoningProgress]
