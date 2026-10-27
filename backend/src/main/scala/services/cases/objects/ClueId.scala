package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class ClueId(value: String)

object ClueId:
  def toString(value: ClueId): String = value.value
  def fromString(value: String): Either[String, ClueId] =
    Either.cond(value.nonEmpty && value == value.trim, ClueId(value), "Invalid ClueId")
  given Encoder[ClueId] = Encoder.encodeString.contramap(toString)
  given Decoder[ClueId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[ClueId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[ClueId] = KeyDecoder.instance(value => fromString(value).toOption)
