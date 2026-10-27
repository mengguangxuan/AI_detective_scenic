package services.games.objects.migration

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*


final case class LegacyCommitSnapshot(session: SessionId, sequence: Sequence, request: RequestId, reducerVersion: String, changes: Vector[io.circe.Json])

object LegacyCommitSnapshot:
  given Encoder[LegacyCommitSnapshot] = deriveEncoder[LegacyCommitSnapshot]
  given Decoder[LegacyCommitSnapshot] = deriveDecoder[LegacyCommitSnapshot]
