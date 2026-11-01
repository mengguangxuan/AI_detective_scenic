package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.cases.objects.scene.SceneObjectBinding
final case class StudySceneView(bundleId: String, revision: String, placeName: String, drawerOpened: Boolean,
  windowOpened: Boolean, objects: Vector[SceneObjectBinding], playerPosition: Option[Vec3] = None, travel: Vector[TravelChoiceView] = Vector.empty)
object StudySceneView:
  given Encoder[StudySceneView] = deriveEncoder[StudySceneView].mapJson { json =>
    val located=if json.hcursor.get[Option[Vec3]]("playerPosition").contains(None) then json.mapObject(_.remove("playerPosition")) else json
    if json.hcursor.get[Vector[TravelChoiceView]]("travel").contains(Vector.empty) then located.mapObject(_.remove("travel")) else located
  }
  given Decoder[StudySceneView] = deriveDecoder[StudySceneView].prepare(_.withFocus(_.mapObject(fields =>
    if fields.contains("travel") then fields else fields.add("travel",io.circe.Json.arr()))))
