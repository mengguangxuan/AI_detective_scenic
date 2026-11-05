package services.models.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

// Encoded local render screenshot. No remote URL, filesystem path or provider credential.
final case class ChatCompletionImage(mediaType: String, base64Data: String)
object ChatCompletionImage:
  given Encoder[ChatCompletionImage] = deriveEncoder[ChatCompletionImage]
  given Decoder[ChatCompletionImage] = deriveDecoder[ChatCompletionImage]
