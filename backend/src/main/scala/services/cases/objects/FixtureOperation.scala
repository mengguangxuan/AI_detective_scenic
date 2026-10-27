package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*


final case class FixtureOperation(id: OperationId, point: PointId, effects: Vector[FixtureEffect], label: String)

object FixtureOperation:
  given Encoder[FixtureOperation] = deriveEncoder[FixtureOperation]
  given Decoder[FixtureOperation] = deriveDecoder[FixtureOperation]
