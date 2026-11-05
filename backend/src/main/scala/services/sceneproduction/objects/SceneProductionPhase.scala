package services.sceneproduction.objects
import io.circe.{Decoder,Encoder}
enum SceneProductionPhase:
  case Draft,Queued,Saved
object SceneProductionPhase:
  given Encoder[SceneProductionPhase] = Encoder.encodeString.contramap(_.toString)
  given Decoder[SceneProductionPhase] = Decoder.decodeString.emap(s => SceneProductionPhase.values.find(_.toString==s).toRight("Invalid phase"))
