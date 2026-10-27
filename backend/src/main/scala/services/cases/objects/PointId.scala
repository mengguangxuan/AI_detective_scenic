package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class PointId(value: String)

object PointId:
  def toString(value: PointId): String = value.value
  def fromString(value: String): Either[String, PointId] =
    Either.cond(value.nonEmpty && value == value.trim, PointId(value), "Invalid PointId")
  given Encoder[PointId] = Encoder.encodeString.contramap(toString)
  given Decoder[PointId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[PointId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[PointId] = KeyDecoder.instance(value => fromString(value).toOption)
