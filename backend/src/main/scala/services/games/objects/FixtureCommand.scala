package services.games.objects

import io.circe.{Decoder, Encoder, Json, DecodingFailure}
import io.circe.syntax.*
import services.cases.objects.*

// Internal finite command protocol, not an exhaustive list of detective actions.
enum FixtureCommand:
  case Execute(operation: OperationId)
  case Investigate(point: PointId, route: RouteId)

object FixtureCommand:
  given Encoder[FixtureCommand] = Encoder.instance {
    case FixtureCommand.Execute(operation) => Json.obj("kind" -> Json.fromString("Execute"), "operation" -> operation.asJson)
    case FixtureCommand.Investigate(point, route) => Json.obj("kind" -> Json.fromString("Investigate"), "point" -> point.asJson, "route" -> route.asJson)
  }
  given Decoder[FixtureCommand] = Decoder.instance { c =>
    c.get[String]("kind").flatMap {
      case "Execute" => c.get[OperationId]("operation").map(FixtureCommand.Execute.apply)
      case "Investigate" => for
        point <- c.get[PointId]("point")
        route <- c.get[RouteId]("route")
      yield FixtureCommand.Investigate(point, route)
      case _ => Left(DecodingFailure("Unsupported command", c.history))
    }
  }
