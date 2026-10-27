package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class InvestigationFixture(caseId: CaseId, revision: Revision, reducerVersion: ReducerVersion, place: PlaceId, spawnPosition: Vec3, nodes: Vector[FixtureNode], points: Vector[FixturePoint], facts: Vector[FixtureFact], clues: Vector[FixtureClue], routes: Vector[FixtureRoute], operations: Vector[FixtureOperation], opened: Set[EntityId], unlocked: Set[EntityId], commonFacts: Set[FactId])

object InvestigationFixture:
  given Encoder[InvestigationFixture] = deriveEncoder[InvestigationFixture]
  given Decoder[InvestigationFixture] = deriveDecoder[InvestigationFixture]
