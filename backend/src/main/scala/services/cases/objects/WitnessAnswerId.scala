package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class WitnessAnswerId(value: String)

object WitnessAnswerId:
  def toString(value: WitnessAnswerId): String = value.value
  def fromString(value: String): Either[String, WitnessAnswerId] =
    Either.cond(value.nonEmpty && value == value.trim, WitnessAnswerId(value), "Invalid WitnessAnswerId")
  given Encoder[WitnessAnswerId] = Encoder.encodeString.contramap(toString)
  given Decoder[WitnessAnswerId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[WitnessAnswerId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[WitnessAnswerId] = KeyDecoder.instance(value => fromString(value).toOption)
