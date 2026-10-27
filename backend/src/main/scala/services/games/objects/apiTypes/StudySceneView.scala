package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class StudySceneView(bundleId: String, revision: String, placeName: String, drawerOpened: Boolean, letterVisible: Boolean)
object StudySceneView:
  given Encoder[StudySceneView] = deriveEncoder[StudySceneView]
  given Decoder[StudySceneView] = deriveDecoder[StudySceneView]
