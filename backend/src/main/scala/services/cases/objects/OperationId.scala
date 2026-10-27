package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class OperationId(value: String)

object OperationId:
  def toString(value: OperationId): String = value.value
  def fromString(value: String): Either[String, OperationId] =
    Either.cond(value.nonEmpty && value == value.trim, OperationId(value), "Invalid OperationId")
  given Encoder[OperationId] = Encoder.encodeString.contramap(toString)
  given Decoder[OperationId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[OperationId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[OperationId] = KeyDecoder.instance(value => fromString(value).toOption)
