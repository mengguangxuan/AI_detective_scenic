package services.sceneproduction.objects
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.UserId
final case class SceneProductionRecord(id: SceneProductionId,owner: UserId,input: SceneAuthorInput,phase: SceneProductionPhase,createdAt: String,savedAt: Option[String])
object SceneProductionRecord:
  given Encoder[SceneProductionRecord] = deriveEncoder[SceneProductionRecord]
  given Decoder[SceneProductionRecord] = deriveDecoder[SceneProductionRecord]
