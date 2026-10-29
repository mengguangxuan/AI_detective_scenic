package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

// One authoring registration per interactive model. Operations live at its point.
final case class SceneObjectBinding(node: SceneNodeId, entity: EntityId, label: String,
  point: Option[PointId], readRoute: Option[RouteId])
object SceneObjectBinding:
  given Encoder[SceneObjectBinding] = deriveEncoder[SceneObjectBinding]
  given Decoder[SceneObjectBinding] = deriveDecoder[SceneObjectBinding]
