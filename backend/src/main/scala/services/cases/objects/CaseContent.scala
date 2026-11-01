package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.utils.LegacyPoliceProfile
import services.cases.objects.scene.{FixtureNode, FixturePoint, SceneObservation, SceneObjectBinding, AreaMap}
import services.cases.objects.content.{FixtureFact, FixtureClue, CasePersonBrief}
import services.cases.objects.dialogue.WitnessProfile
import services.cases.objects.investigation.{ContinuousInterviewSpec, ExternalCheckSpec, FixtureOperation, FixtureRoute, InvestigationClock, InvestigationStage, WitnessFollowUpSpec, StatementComparisonSpec, CaseConclusionSpec}


final case class CaseContent(place: PlaceId, placeName: String, initialStoryTimeSeconds: Long, timeOriginLabel: String,
  spawnPosition: Vec3, nodes: Vector[FixtureNode], points: Vector[FixturePoint],
  facts: Vector[FixtureFact], clues: Vector[FixtureClue], routes: Vector[FixtureRoute],
  operations: Vector[FixtureOperation], initiallyOpened: Set[EntityId],
  initiallyUnlocked: Set[EntityId], commonFacts: Set[FactId], observations: Vector[SceneObservation] = Vector.empty,
  stage: Option[InvestigationStage] = None, sceneObjects: Vector[SceneObjectBinding] = Vector.empty,
  witnesses: Vector[WitnessProfile] = Vector.empty, police: Option[WitnessId] = None,
  publicPersons: Vector[CasePersonBrief] = Vector.empty, areaMap: Option[AreaMap] = None,
  clock: Option[InvestigationClock] = None, externalChecks: Vector[ExternalCheckSpec] = Vector.empty,
  followUps: Vector[WitnessFollowUpSpec] = Vector.empty, statementComparisons: Vector[StatementComparisonSpec] = Vector.empty, conclusion: Option[CaseConclusionSpec] = None,
  continuousInterviews: Vector[ContinuousInterviewSpec] = Vector.empty, interviewPromptVersion: Int = 1)

object CaseContent:
  given Encoder[CaseContent] = deriveEncoder[CaseContent].mapJson(json =>
    val versioned=if json.hcursor.get[Int]("interviewPromptVersion").contains(1) then json.mapObject(_.remove("interviewPromptVersion")) else json
    if json.hcursor.get[Vector[ContinuousInterviewSpec]]("continuousInterviews").contains(Vector.empty) then versioned.mapObject(_.remove("continuousInterviews")) else versioned)
  private val decoder: Decoder[CaseContent] = deriveDecoder[CaseContent].prepare(_.withFocus(json =>
    json.mapObject(original =>
      val obj=if original.contains("interviewPromptVersion") then original else original.add("interviewPromptVersion",io.circe.Json.fromInt(1))
      Vector("observations", "sceneObjects", "witnesses", "publicPersons", "externalChecks", "followUps", "statementComparisons", "continuousInterviews").foldLeft(obj)((fields, name) =>
      if fields.contains(name) then fields else fields.add(name,io.circe.Json.arr())))))

  given Decoder[CaseContent] = Decoder.instance { cursor =>
    LegacyPoliceProfile.normalize(cursor).flatMap(decoder.decodeJson)
  }
