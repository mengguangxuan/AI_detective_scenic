package services.system.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class RequestId(value: String)

object RequestId:
  def toString(value: RequestId): String = value.value
  def fromString(value: String): Either[String, RequestId] =
    Either.cond(value.nonEmpty && value == value.trim, RequestId(value), "Invalid RequestId")
  given Encoder[RequestId] = Encoder.encodeString.contramap(toString)
  given Decoder[RequestId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[RequestId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[RequestId] = KeyDecoder.instance(value => fromString(value).toOption)
