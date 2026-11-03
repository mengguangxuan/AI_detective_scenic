package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.cases.objects.content.FixtureClue
import services.cases.objects.dialogue.WitnessProfile

final case class InterviewContext(profile: WitnessProfile, evidence: Vector[FixtureClue], history: Vector[InterviewTurnRecord], caseId: CaseId, responseVersion: Int = 1,
  statementAnswers: Set[WitnessAnswerId] = Set.empty, recordedStatements: Vector[FixtureClue] = Vector.empty, promptVersion: Int = 1,
  activeFocus: Option[RecordedDialogueFocus] = None, claimOptions: Vector[InterviewClaimOption] = Vector.empty)
object InterviewContext:
  given Encoder[InterviewContext] = deriveEncoder[InterviewContext].mapJson(json =>
    val semantic=if json.hcursor.get[Vector[InterviewClaimOption]]("claimOptions").contains(Vector.empty) then json.mapObject(_.remove("claimOptions")) else json
    val focused=if json.hcursor.get[Option[RecordedDialogueFocus]]("activeFocus").contains(None) then semantic.mapObject(_.remove("activeFocus")) else semantic
    val versioned=if json.hcursor.get[Int]("promptVersion").contains(1) then focused.mapObject(_.remove("promptVersion")) else focused
    if json.hcursor.get[Int]("responseVersion").exists(_ < 3) && json.hcursor.get[Int]("promptVersion").contains(1) then versioned.mapObject(_.remove("statementAnswers").remove("recordedStatements")) else versioned)
  given Decoder[InterviewContext] = deriveDecoder[InterviewContext].prepare(_.withFocus(_.mapObject(fields =>
    val configured=if fields.contains("promptVersion") then fields else fields.add("promptVersion",io.circe.Json.fromInt(1))
    val versioned=if configured.contains("responseVersion") then configured else configured.add("responseVersion",io.circe.Json.fromInt(1))
    Vector("statementAnswers","recordedStatements","claimOptions").foldLeft(versioned)((obj,name) => if obj.contains(name) then obj else obj.add(name,io.circe.Json.arr())))))
