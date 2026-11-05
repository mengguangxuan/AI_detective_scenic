package services.sceneproduction.objects
import io.circe.{Decoder,Encoder,Json}
import io.circe.generic.semiauto.*
final case class SceneAuthorInput(title: String,targetPlaceId: String,fields: Json)
object SceneAuthorInput:
  given Encoder[SceneAuthorInput] = deriveEncoder[SceneAuthorInput]
  given Decoder[SceneAuthorInput] = deriveDecoder[SceneAuthorInput]
