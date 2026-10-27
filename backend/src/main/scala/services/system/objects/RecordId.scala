package services.system.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class RecordId(value: String)

object RecordId:
  def toString(value: RecordId): String = value.value
  def fromString(value: String): Either[String, RecordId] =
    Either.cond(value.nonEmpty && value == value.trim, RecordId(value), "Invalid RecordId")
  given Encoder[RecordId] = Encoder.encodeString.contramap(toString)
  given Decoder[RecordId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[RecordId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[RecordId] = KeyDecoder.instance(value => fromString(value).toOption)
