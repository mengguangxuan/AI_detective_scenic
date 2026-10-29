package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class InterviewContext(profile: WitnessProfile, evidence: Vector[FixtureClue], history: Vector[InterviewTurnRecord], caseId: CaseId, revision: Revision, responseVersion: Int = 1)
object InterviewContext:
  given Encoder[InterviewContext] = deriveEncoder[InterviewContext]
  given Decoder[InterviewContext] = deriveDecoder[InterviewContext].prepare(_.withFocus(_.mapObject(fields =>
    if fields.contains("responseVersion") then fields else fields.add("responseVersion",io.circe.Json.fromInt(1)))))
