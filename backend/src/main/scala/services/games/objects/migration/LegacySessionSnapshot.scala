package services.games.objects.migration

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*


final case class LegacySessionSnapshot(id: SessionId, owner: UserId, fixture: io.circe.Json, initialState: LegacyState, sequence: Sequence)

object LegacySessionSnapshot:
  given Encoder[LegacySessionSnapshot] = deriveEncoder[LegacySessionSnapshot]
  given Decoder[LegacySessionSnapshot] = deriveDecoder[LegacySessionSnapshot]
