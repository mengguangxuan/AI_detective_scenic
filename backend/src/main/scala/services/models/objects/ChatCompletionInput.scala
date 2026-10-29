package services.models.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*


final case class ChatCompletionInput(system: String, user: String)
object ChatCompletionInput:
  given Encoder[ChatCompletionInput] = deriveEncoder[ChatCompletionInput]
  given Decoder[ChatCompletionInput] = deriveDecoder[ChatCompletionInput]
