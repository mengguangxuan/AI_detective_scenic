package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.games.objects.apiTypes.*

final case class FixtureInvestigationRecord(id: RecordId, point: PointId, route: RouteId, text: Text, grantedClues: Set[ClueId])

object FixtureInvestigationRecord:
  given Encoder[FixtureInvestigationRecord] = deriveEncoder[FixtureInvestigationRecord]
  given Decoder[FixtureInvestigationRecord] = deriveDecoder[FixtureInvestigationRecord]
