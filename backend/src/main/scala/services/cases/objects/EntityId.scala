package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class EntityId(value: String)

object EntityId:
  def toString(value: EntityId): String = value.value
  def fromString(value: String): Either[String, EntityId] =
    Either.cond(value.nonEmpty && value == value.trim, EntityId(value), "Invalid EntityId")
  given Encoder[EntityId] = Encoder.encodeString.contramap(toString)
  given Decoder[EntityId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[EntityId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[EntityId] = KeyDecoder.instance(value => fromString(value).toOption)
