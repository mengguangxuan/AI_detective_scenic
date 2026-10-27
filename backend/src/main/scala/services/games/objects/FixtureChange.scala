package services.games.objects

import io.circe.{Decoder, Encoder, Json, DecodingFailure}
import io.circe.syntax.*
import services.cases.objects.*

enum FixtureChange:
  case WorldChanged(effects: Vector[FixtureEffect])
  case OperationCompleted(operation: OperationId)
  case Investigated(record: FixtureInvestigationRecord)
  case Acquired(record: FixtureAcquisition)

object FixtureChange:
  given Encoder[FixtureChange] = Encoder.instance {
    case FixtureChange.WorldChanged(effects) => Json.obj("kind" -> Json.fromString("WorldChanged"), "effects" -> effects.asJson)
    case FixtureChange.OperationCompleted(operation) => Json.obj("kind" -> Json.fromString("OperationCompleted"), "operation" -> operation.asJson)
    case FixtureChange.Investigated(record) => Json.obj("kind" -> Json.fromString("Investigated"), "record" -> record.asJson)
    case FixtureChange.Acquired(record) => Json.obj("kind" -> Json.fromString("Acquired"), "record" -> record.asJson)
  }
  given Decoder[FixtureChange] = Decoder.instance { cursor =>
    cursor.get[String]("kind").flatMap {
      case "WorldChanged" => cursor.get[Vector[FixtureEffect]]("effects").map(FixtureChange.WorldChanged.apply)
      case "OperationCompleted" => cursor.get[OperationId]("operation").map(FixtureChange.OperationCompleted.apply)
      case "Investigated" => cursor.get[FixtureInvestigationRecord]("record").map(FixtureChange.Investigated.apply)
      case "Acquired" => cursor.get[FixtureAcquisition]("record").map(FixtureChange.Acquired.apply)
      case _ => Left(DecodingFailure("Unsupported change", cursor.history))
    }
  }
