package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class SceneNodeId(value: String)

object SceneNodeId:
  def toString(value: SceneNodeId): String = value.value
  def fromString(value: String): Either[String, SceneNodeId] =
    Either.cond(value.nonEmpty && value == value.trim, SceneNodeId(value), "Invalid SceneNodeId")
  given Encoder[SceneNodeId] = Encoder.encodeString.contramap(toString)
  given Decoder[SceneNodeId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[SceneNodeId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[SceneNodeId] = KeyDecoder.instance(value => fromString(value).toOption)
