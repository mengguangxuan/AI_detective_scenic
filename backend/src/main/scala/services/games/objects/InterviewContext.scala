package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.cases.objects.content.FixtureClue
import services.cases.objects.dialogue.WitnessProfile

final case class InterviewContext(profile: WitnessProfile, evidence: Vector[FixtureClue], history: Vector[InterviewTurnRecord], caseId: CaseId, revision: Revision, responseVersion: Int = 1,
  statementAnswers: Set[WitnessAnswerId] = Set.empty, recordedStatements: Vector[FixtureClue] = Vector.empty)
object InterviewContext:
  given Encoder[InterviewContext] = deriveEncoder[InterviewContext].mapJson(json =>
    if json.hcursor.get[Int]("responseVersion").exists(_ < 3) then json.mapObject(_.remove("statementAnswers").remove("recordedStatements")) else json)
  given Decoder[InterviewContext] = deriveDecoder[InterviewContext].prepare(_.withFocus(_.mapObject(fields =>
    val versioned=if fields.contains("responseVersion") then fields else fields.add("responseVersion",io.circe.Json.fromInt(1))
    Vector("statementAnswers","recordedStatements").foldLeft(versioned)((obj,name) => if obj.contains(name) then obj else obj.add(name,io.circe.Json.arr())))))
