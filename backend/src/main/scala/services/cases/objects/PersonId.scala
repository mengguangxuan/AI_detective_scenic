package services.cases.objects

import io.circe.{Decoder,Encoder}

final case class PersonId(value: String)
object PersonId:
  given Encoder[PersonId] = Encoder.encodeString.contramap(_.value)
  given Decoder[PersonId] = Decoder.decodeString.map(PersonId.apply)
