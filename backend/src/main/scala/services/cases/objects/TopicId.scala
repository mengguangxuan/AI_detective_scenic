package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class TopicId(value: String)

object TopicId:
  def toString(value: TopicId): String = value.value
  def fromString(value: String): Either[String, TopicId] =
    Either.cond(value.nonEmpty && value == value.trim, TopicId(value), "Invalid TopicId")
  given Encoder[TopicId] = Encoder.encodeString.contramap(toString)
  given Decoder[TopicId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[TopicId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[TopicId] = KeyDecoder.instance(value => fromString(value).toOption)
