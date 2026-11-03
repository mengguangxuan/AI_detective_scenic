package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.cases.objects.scene.SceneObjectBinding
final case class SceneView(bundleId: String, placeName: String, openedEntities: Set[EntityId], objects: Vector[SceneObjectBinding], playerPosition: Option[Vec3] = None, travel: Vector[TravelChoiceView] = Vector.empty, placeId: Option[PlaceId] = None, render: Option[SceneRenderView] = None)
object SceneView:
  given Encoder[SceneView] = deriveEncoder[SceneView].mapJson { json =>
    val located=if json.hcursor.get[Option[Vec3]]("playerPosition").contains(None) then json.mapObject(_.remove("playerPosition")) else json
    if json.hcursor.get[Vector[TravelChoiceView]]("travel").contains(Vector.empty) then located.mapObject(_.remove("travel")) else located
  }
  given Decoder[SceneView] = deriveDecoder[SceneView].prepare(_.withFocus(_.mapObject(fields =>
    if fields.contains("travel") then fields else fields.add("travel",io.circe.Json.arr()))))
