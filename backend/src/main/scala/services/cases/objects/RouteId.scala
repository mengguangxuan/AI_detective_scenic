package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class RouteId(value: String)

object RouteId:
  def toString(value: RouteId): String = value.value
  def fromString(value: String): Either[String, RouteId] =
    Either.cond(value.nonEmpty && value == value.trim, RouteId(value), "Invalid RouteId")
  given Encoder[RouteId] = Encoder.encodeString.contramap(toString)
  given Decoder[RouteId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[RouteId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[RouteId] = KeyDecoder.instance(value => fromString(value).toOption)
