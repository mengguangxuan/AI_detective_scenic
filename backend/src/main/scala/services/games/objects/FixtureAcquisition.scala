package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.games.objects.apiTypes.*

final case class FixtureAcquisition(id: RecordId, sourceRecord: RecordId, clues: Set[ClueId], facts: Set[FactId])

object FixtureAcquisition:
  given Encoder[FixtureAcquisition] = deriveEncoder[FixtureAcquisition]
  given Decoder[FixtureAcquisition] = deriveDecoder[FixtureAcquisition]
