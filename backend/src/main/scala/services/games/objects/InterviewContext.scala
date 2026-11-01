package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.cases.objects.content.FixtureClue
import services.cases.objects.dialogue.WitnessProfile

final case class InterviewContext(profile: WitnessProfile, evidence: Vector[FixtureClue], history: Vector[InterviewTurnRecord], caseId: CaseId, revision: Revision, responseVersion: Int = 1,
  statementAnswers: Set[WitnessAnswerId] = Set.empty, recordedStatements: Vector[FixtureClue] = Vector.empty, promptVersion: Int = 1)
object InterviewContext:
  given Encoder[InterviewContext] = deriveEncoder[InterviewContext].mapJson(json =>
    val versioned=if json.hcursor.get[Int]("promptVersion").contains(1) then json.mapObject(_.remove("promptVersion")) else json
    if json.hcursor.get[Int]("responseVersion").exists(_ < 3) && json.hcursor.get[Int]("promptVersion").contains(1) then versioned.mapObject(_.remove("statementAnswers").remove("recordedStatements")) else versioned)
  given Decoder[InterviewContext] = deriveDecoder[InterviewContext].prepare(_.withFocus(_.mapObject(fields =>
    val configured=if fields.contains("promptVersion") then fields else fields.add("promptVersion",io.circe.Json.fromInt(1))
    val versioned=if configured.contains("responseVersion") then configured else configured.add("responseVersion",io.circe.Json.fromInt(1))
    Vector("statementAnswers","recordedStatements").foldLeft(versioned)((obj,name) => if obj.contains(name) then obj else obj.add(name,io.circe.Json.arr())))))
