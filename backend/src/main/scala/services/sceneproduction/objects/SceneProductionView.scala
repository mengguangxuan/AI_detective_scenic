package services.sceneproduction.objects
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
final case class SceneProductionProgress(generationCalls: Int,repairAttempts: Int,runtimeCheckAttempts: Int,visualReviewAttempts: Int,overallReviewAttempts: Int)
object SceneProductionProgress:
  given Encoder[SceneProductionProgress] = deriveEncoder[SceneProductionProgress]
  given Decoder[SceneProductionProgress] = deriveDecoder[SceneProductionProgress]
final case class SceneProductionIssue(message: String,severity: String)
object SceneProductionIssue:
  given Encoder[SceneProductionIssue] = deriveEncoder[SceneProductionIssue]
  given Decoder[SceneProductionIssue] = deriveDecoder[SceneProductionIssue]
final case class SceneLayoutItem(id: String,label: String,x: Double,z: Double,width: Double,depth: Double,rotationY: Double)
object SceneLayoutItem:
  given Encoder[SceneLayoutItem] = deriveEncoder[SceneLayoutItem]
  given Decoder[SceneLayoutItem] = deriveDecoder[SceneLayoutItem]
final case class SceneLayoutView(title: String,decisions: Vector[String],items: Vector[SceneLayoutItem])
object SceneLayoutView:
  given Encoder[SceneLayoutView] = deriveEncoder[SceneLayoutView]
  given Decoder[SceneLayoutView] = deriveDecoder[SceneLayoutView]
final case class SceneProductionStatus(stage: String,label: String,message: String,round: Int,maxRounds: Int,calls: Int,tokens: Long,progress: SceneProductionProgress,issues: Vector[SceneProductionIssue],layout: Option[SceneLayoutView] = None)
object SceneProductionStatus:
  given Encoder[SceneProductionStatus] = deriveEncoder[SceneProductionStatus]
  given Decoder[SceneProductionStatus] = deriveDecoder[SceneProductionStatus]
final case class SceneProductionView(id: SceneProductionId,input: SceneAuthorInput,phase: SceneProductionPhase,createdAt: String,savedAt: Option[String],status: SceneProductionStatus,previewUrl: Option[String])
object SceneProductionView:
  given Encoder[SceneProductionView] = deriveEncoder[SceneProductionView]
  given Decoder[SceneProductionView] = deriveDecoder[SceneProductionView]
