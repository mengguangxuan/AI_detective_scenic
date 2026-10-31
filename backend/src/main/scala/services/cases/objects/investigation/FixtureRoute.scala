package services.cases.objects.investigation

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.{ClueId, OperationId, PointId, RouteId}


final case class FixtureRoute(id: RouteId, point: PointId, prerequisite: Option[OperationId], clues: Vector[ClueId], label: String,
  requiredClues: Set[ClueId] = Set.empty, presentedClues: Set[ClueId] = Set.empty, disabledAfter: Option[OperationId] = None,
  reply: Option[String] = None)

object FixtureRoute:
  given Encoder[FixtureRoute] = deriveEncoder[FixtureRoute]
  given Decoder[FixtureRoute] = deriveDecoder[FixtureRoute].prepare(_.withFocus(_.mapObject(obj =>
    Vector("requiredClues","presentedClues").foldLeft(obj)((fields,name) =>
      if fields.contains(name) then fields else fields.add(name,io.circe.Json.arr())))))
