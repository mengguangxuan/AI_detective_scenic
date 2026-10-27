package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class FactId(value: String)

object FactId:
  def toString(value: FactId): String = value.value
  def fromString(value: String): Either[String, FactId] =
    Either.cond(value.nonEmpty && value == value.trim, FactId(value), "Invalid FactId")
  given Encoder[FactId] = Encoder.encodeString.contramap(toString)
  given Decoder[FactId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[FactId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[FactId] = KeyDecoder.instance(value => fromString(value).toOption)
