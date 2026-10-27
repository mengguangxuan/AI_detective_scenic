package services.system.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class Text(value: String)

object Text:
  def toString(value: Text): String = value.value
  def fromString(value: String): Either[String, Text] =
    Either.cond(value.trim.nonEmpty, Text(value), "Invalid Text")
  given Encoder[Text] = Encoder.encodeString.contramap(toString)
  given Decoder[Text] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[Text] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[Text] = KeyDecoder.instance(value => fromString(value).toOption)
