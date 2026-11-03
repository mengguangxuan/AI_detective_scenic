package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class PersonId(value: String)
object PersonId:
  given Encoder[PersonId] = Encoder.encodeString.contramap(_.value)
  given Decoder[PersonId] = Decoder.decodeString.map(PersonId.apply)
  given KeyEncoder[PersonId] = KeyEncoder.encodeKeyString.contramap(_.value)
  given KeyDecoder[PersonId] = KeyDecoder.decodeKeyString.map(PersonId.apply)
