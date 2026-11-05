package services.sceneproduction.objects
import io.circe.Decoder
import io.circe.generic.semiauto.*
final case class ResumeSceneProductionInput(productionId: SceneProductionId,expectedCalls: Int)
object ResumeSceneProductionInput:
  given Decoder[ResumeSceneProductionInput]=deriveDecoder[ResumeSceneProductionInput]
