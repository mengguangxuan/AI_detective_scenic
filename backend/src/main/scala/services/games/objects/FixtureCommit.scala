package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.games.objects.apiTypes.*

final case class FixtureCommit(session: SessionId, sequence: Sequence, request: RequestId, reducerVersion: ReducerVersion, changes: Vector[FixtureChange])

object FixtureCommit:
  given Encoder[FixtureCommit] = deriveEncoder[FixtureCommit]
  given Decoder[FixtureCommit] = deriveDecoder[FixtureCommit]
