package services.sceneproduction.objects
import io.circe.Decoder
import io.circe.generic.semiauto.*
final case class SceneProductionRef(productionId: SceneProductionId)
object SceneProductionRef:
  given Decoder[SceneProductionRef] = deriveDecoder[SceneProductionRef]
final case class SaveSceneDraftInput(productionId: SceneProductionId,input: SceneAuthorInput)
object SaveSceneDraftInput:
  given Decoder[SaveSceneDraftInput] = deriveDecoder[SaveSceneDraftInput]
