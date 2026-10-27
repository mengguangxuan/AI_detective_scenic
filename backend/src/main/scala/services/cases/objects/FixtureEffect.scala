package services.cases.objects

import io.circe.{Decoder, Encoder, Json, DecodingFailure}
import io.circe.syntax.*

// Closed execution protocol supported by the fixture runner.
enum FixtureEffect:
  case Open(entity: EntityId)
  case SetOpened(entity: EntityId, opened: Boolean)
  case SetNodeVisible(node: SceneNodeId, visible: Boolean)

object FixtureEffect:
  given Encoder[FixtureEffect] = Encoder.instance {
    case FixtureEffect.Open(entity) => Json.obj("kind" -> Json.fromString("Open"), "entity" -> entity.asJson)
    case FixtureEffect.SetOpened(entity, opened) => Json.obj("kind" -> Json.fromString("SetOpened"), "entity" -> entity.asJson, "opened" -> opened.asJson)
    case FixtureEffect.SetNodeVisible(node, visible) => Json.obj("kind" -> Json.fromString("SetNodeVisible"), "node" -> node.asJson, "visible" -> visible.asJson)
  }
  given Decoder[FixtureEffect] = Decoder.instance { cursor =>
    cursor.get[String]("kind").flatMap {
      case "Open" => cursor.get[EntityId]("entity").map(FixtureEffect.Open.apply)
      case "SetOpened" => for
        entity <- cursor.get[EntityId]("entity")
        opened <- cursor.get[Boolean]("opened")
      yield FixtureEffect.SetOpened(entity, opened)
      case "SetNodeVisible" => for
        node <- cursor.get[SceneNodeId]("node")
        visible <- cursor.get[Boolean]("visible")
      yield FixtureEffect.SetNodeVisible(node, visible)
      case _ => Left(DecodingFailure("Unsupported fixture effect", cursor.history))
    }
  }
