package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class CaseEventId(value: String)

object CaseEventId:
  def toString(value: CaseEventId): String = value.value
  def fromString(value: String): Either[String, CaseEventId] =
    Either.cond(value.nonEmpty && value == value.trim, CaseEventId(value), "Invalid CaseEventId")
  given Encoder[CaseEventId] = Encoder.encodeString.contramap(toString)
  given Decoder[CaseEventId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[CaseEventId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[CaseEventId] = KeyDecoder.instance(value => fromString(value).toOption)
