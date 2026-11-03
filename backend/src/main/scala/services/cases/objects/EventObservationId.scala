package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class EventObservationId(value: String)

object EventObservationId:
  def toString(value: EventObservationId): String = value.value
  def fromString(value: String): Either[String, EventObservationId] =
    Either.cond(value.nonEmpty && value == value.trim, EventObservationId(value), "Invalid EventObservationId")
  given Encoder[EventObservationId] = Encoder.encodeString.contramap(toString)
  given Decoder[EventObservationId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[EventObservationId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[EventObservationId] = KeyDecoder.instance(value => fromString(value).toOption)
