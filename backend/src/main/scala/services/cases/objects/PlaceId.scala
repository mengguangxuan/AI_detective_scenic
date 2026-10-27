package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class PlaceId(value: String)

object PlaceId:
  def toString(value: PlaceId): String = value.value
  def fromString(value: String): Either[String, PlaceId] =
    Either.cond(value.nonEmpty && value == value.trim, PlaceId(value), "Invalid PlaceId")
  given Encoder[PlaceId] = Encoder.encodeString.contramap(toString)
  given Decoder[PlaceId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[PlaceId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[PlaceId] = KeyDecoder.instance(value => fromString(value).toOption)
