package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class EventPlanId(value: String)

object EventPlanId:
  def toString(value: EventPlanId): String = value.value
  def fromString(value: String): Either[String, EventPlanId] =
    Either.cond(value.nonEmpty && value == value.trim, EventPlanId(value), "Invalid EventPlanId")
  given Encoder[EventPlanId] = Encoder.encodeString.contramap(toString)
  given Decoder[EventPlanId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[EventPlanId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[EventPlanId] = KeyDecoder.instance(value => fromString(value).toOption)
