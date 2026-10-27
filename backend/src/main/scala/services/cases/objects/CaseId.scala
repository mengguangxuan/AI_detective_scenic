package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class CaseId(value: String)

object CaseId:
  def toString(value: CaseId): String = value.value
  def fromString(value: String): Either[String, CaseId] =
    Either.cond(value.nonEmpty && value == value.trim, CaseId(value), "Invalid CaseId")
  given Encoder[CaseId] = Encoder.encodeString.contramap(toString)
  given Decoder[CaseId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[CaseId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[CaseId] = KeyDecoder.instance(value => fromString(value).toOption)
