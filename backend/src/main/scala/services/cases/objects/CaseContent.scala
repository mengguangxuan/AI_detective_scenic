package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*


final case class CaseContent(place: PlaceId, placeName: String, initialStoryTimeSeconds: Long, timeOriginLabel: String,
  spawnPosition: Vec3, nodes: Vector[FixtureNode], points: Vector[FixturePoint],
  facts: Vector[FixtureFact], clues: Vector[FixtureClue], routes: Vector[FixtureRoute],
  operations: Vector[FixtureOperation], initiallyOpened: Set[EntityId],
  initiallyUnlocked: Set[EntityId], commonFacts: Set[FactId], observations: Vector[SceneObservation] = Vector.empty,
  stage: Option[InvestigationStage] = None, sceneObjects: Vector[SceneObjectBinding] = Vector.empty,
  witnesses: Vector[WitnessProfile] = Vector.empty, police: Option[PoliceContact] = None,
  publicPersons: Vector[CasePersonBrief] = Vector.empty, areaMap: Option[AreaMap] = None,
  clock: Option[InvestigationClock] = None, externalChecks: Vector[ExternalCheckSpec] = Vector.empty,
  followUps: Vector[WitnessFollowUpSpec] = Vector.empty, statementComparisons: Vector[StatementComparisonSpec] = Vector.empty)

object CaseContent:
  given Encoder[CaseContent] = deriveEncoder[CaseContent]
  given Decoder[CaseContent] = deriveDecoder[CaseContent].prepare(_.withFocus(json =>
    json.mapObject(obj => Vector("observations", "sceneObjects", "witnesses", "publicPersons", "externalChecks", "followUps", "statementComparisons").foldLeft(obj)((fields, name) =>
      if fields.contains(name) then fields else fields.add(name,io.circe.Json.arr())))))
