package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class WitnessId(value: String)

object WitnessId:
  def toString(value: WitnessId): String = value.value
  def fromString(value: String): Either[String, WitnessId] =
    Either.cond(value.nonEmpty && value == value.trim, WitnessId(value), "Invalid WitnessId")
  given Encoder[WitnessId] = Encoder.encodeString.contramap(toString)
  given Decoder[WitnessId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[WitnessId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[WitnessId] = KeyDecoder.instance(value => fromString(value).toOption)
