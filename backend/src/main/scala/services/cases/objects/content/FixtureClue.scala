package services.cases.objects.content

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.{ClueId, FactId}

final case class FixtureClue(id: ClueId, title: Text, text: Text, facts: Set[FactId])

object FixtureClue:
  given Encoder[FixtureClue] = deriveEncoder[FixtureClue]
  given Decoder[FixtureClue] = deriveDecoder[FixtureClue]
