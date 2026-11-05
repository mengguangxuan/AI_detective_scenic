package services.sceneproduction.objects
import io.circe.{Decoder,Encoder}
final case class SceneProductionId(value: String)
object SceneProductionId:
  given Encoder[SceneProductionId] = Encoder.encodeString.contramap(_.value)
  given Decoder[SceneProductionId] = Decoder.decodeString.emap(s =>
    if s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") then Right(SceneProductionId(s)) else Left("Invalid production ID"))
