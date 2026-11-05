package services.models.objects
import io.circe.Encoder
import io.circe.generic.semiauto.*
// Internal worker result; provider envelopes and credentials remain inside the model service.
final case class SceneCompletionResult(content: String,totalTokens: Long)
object SceneCompletionResult:
  given Encoder[SceneCompletionResult] = deriveEncoder[SceneCompletionResult]
