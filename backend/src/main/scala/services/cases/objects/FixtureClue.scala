package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class FixtureClue(id: ClueId, title: Text, text: Text, facts: Set[FactId])

object FixtureClue:
  given Encoder[FixtureClue] = deriveEncoder[FixtureClue]
  given Decoder[FixtureClue] = deriveDecoder[FixtureClue]
