package services.system.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class SessionId(value: String)

object SessionId:
  def toString(value: SessionId): String = value.value
  def fromString(value: String): Either[String, SessionId] =
    Either.cond(value.nonEmpty && value == value.trim, SessionId(value), "Invalid SessionId")
  given Encoder[SessionId] = Encoder.encodeString.contramap(toString)
  given Decoder[SessionId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[SessionId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[SessionId] = KeyDecoder.instance(value => fromString(value).toOption)
