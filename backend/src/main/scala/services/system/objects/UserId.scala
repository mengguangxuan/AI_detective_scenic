package services.system.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class UserId(value: String)

object UserId:
  def toString(value: UserId): String = value.value
  def fromString(value: String): Either[String, UserId] =
    Either.cond(value.nonEmpty && value == value.trim, UserId(value), "Invalid UserId")
  given Encoder[UserId] = Encoder.encodeString.contramap(toString)
  given Decoder[UserId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[UserId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[UserId] = KeyDecoder.instance(value => fromString(value).toOption)
