package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*


final case class CaseContent(place: PlaceId, placeName: String, initialStoryTimeSeconds: Long, timeOriginLabel: String,
  spawnPosition: Vec3, nodes: Vector[FixtureNode], points: Vector[FixturePoint],
  facts: Vector[FixtureFact], clues: Vector[FixtureClue], routes: Vector[FixtureRoute],
  operations: Vector[FixtureOperation], initiallyOpened: Set[EntityId],
  initiallyUnlocked: Set[EntityId], commonFacts: Set[FactId])

object CaseContent:
  given Encoder[CaseContent] = deriveEncoder[CaseContent]
  given Decoder[CaseContent] = deriveDecoder[CaseContent]
