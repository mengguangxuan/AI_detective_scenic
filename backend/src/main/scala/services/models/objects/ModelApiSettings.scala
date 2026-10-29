package services.models.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*


final case class ModelApiSettings(baseUrl: String, apiKey: String, model: String, timeoutSeconds: Int, maxTokens: Int, disableThinking: Boolean)
object ModelApiSettings:
  given Encoder[ModelApiSettings] = deriveEncoder[ModelApiSettings]
  given Decoder[ModelApiSettings] = deriveDecoder[ModelApiSettings]
