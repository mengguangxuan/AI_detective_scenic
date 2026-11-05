package services.models.objects
import io.circe.Encoder
import io.circe.generic.semiauto.*
// Internal diagnostics only. No provider headers, error body, URL or credentials.
// partialContent is a bounded model completion, never a trusted scene artifact.
final case class ModelRequestFailure(code: ModelFailureCode,providerStatus: Option[Int]=None,finishReason: Option[String]=None,responseBytes: Option[Int]=None,contentChars: Option[Int]=None,totalTokens: Long=0,maxTokens: Option[Int]=None,partialContent: Option[String]=None) extends RuntimeException(s"Model request unavailable: $code")
object ModelRequestFailure:
  given Encoder[ModelRequestFailure]=deriveEncoder[ModelRequestFailure]
