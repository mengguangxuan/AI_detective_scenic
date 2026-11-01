package services.models.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

// A completed dialogue pair; callers cannot inject system roles through history.
final case class ChatCompletionExchange(user: String, assistant: String)
object ChatCompletionExchange:
  given Encoder[ChatCompletionExchange] = deriveEncoder[ChatCompletionExchange]
  given Decoder[ChatCompletionExchange] = deriveDecoder[ChatCompletionExchange]
