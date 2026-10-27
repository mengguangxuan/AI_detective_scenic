package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class FixtureFact(id: FactId, content: Text)

object FixtureFact:
  given Encoder[FixtureFact] = deriveEncoder[FixtureFact]
  given Decoder[FixtureFact] = deriveDecoder[FixtureFact]
