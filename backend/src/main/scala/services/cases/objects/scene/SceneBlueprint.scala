package services.cases.objects.scene

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.{PlaceId,SceneNodeId}
import services.system.objects.Vec3

// Registered primitives are data, never executable author/model code.
enum PrimitiveShape:
  case Box, Cylinder, Sphere
object PrimitiveShape:
  given Encoder[PrimitiveShape] = Encoder.encodeString.contramap(_.toString)
  given Decoder[PrimitiveShape] = Decoder.decodeString.emap(s => PrimitiveShape.values.find(_.toString == s).toRight("Unknown primitive"))
final case class ScenePrimitive(shape: PrimitiveShape, size: Vec3, offset: Vec3, color: String)
object ScenePrimitive:
  given Encoder[ScenePrimitive] = deriveEncoder[ScenePrimitive]
  given Decoder[ScenePrimitive] = deriveDecoder[ScenePrimitive]
final case class SceneVisual(node: SceneNodeId, parts: Vector[ScenePrimitive])
object SceneVisual:
  given Encoder[SceneVisual] = deriveEncoder[SceneVisual]
  given Decoder[SceneVisual] = deriveDecoder[SceneVisual]
final case class SceneRoom(place: PlaceId, description: String, floorSize: Vec3, color: String, cameraPosition: Vec3, cameraTarget: Vec3)
object SceneRoom:
  given Encoder[SceneRoom] = deriveEncoder[SceneRoom]
  given Decoder[SceneRoom] = deriveDecoder[SceneRoom]
final case class SceneBlueprint(rooms: Vector[SceneRoom], visuals: Vector[SceneVisual])
object SceneBlueprint:
  given Encoder[SceneBlueprint] = deriveEncoder[SceneBlueprint]
  given Decoder[SceneBlueprint] = deriveDecoder[SceneBlueprint]
