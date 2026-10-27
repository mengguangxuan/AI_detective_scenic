package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class FixtureRoute(id: RouteId, point: PointId, prerequisite: OperationId, clues: Vector[ClueId])

object FixtureRoute:
  given Encoder[FixtureRoute] = deriveEncoder[FixtureRoute]
  given Decoder[FixtureRoute] = deriveDecoder[FixtureRoute]
