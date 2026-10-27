package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.games.objects.apiTypes.*

final case class StoredSession(id: SessionId, owner: UserId, fixture: InvestigationFixture, initialState: FixtureState, sequence: Sequence)

object StoredSession:
  given Encoder[StoredSession] = deriveEncoder[StoredSession]
  given Decoder[StoredSession] = deriveDecoder[StoredSession]
