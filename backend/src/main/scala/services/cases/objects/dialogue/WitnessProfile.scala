package services.cases.objects.dialogue

import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax.*
import services.cases.objects.{PointId, WitnessId}
import services.cases.objects.investigation.WitnessAnswer

// Compiled interview projection, also used in frozen contexts. New authoring uses
// CharacterProfile + WitnessInteraction; keep the original flat JSON for saved requests.
final case class WitnessProfile(id: WitnessId, point: PointId, name: String, background: String,
  voice: String, answers: Vector[WitnessAnswer], dossier: DialogueDossier = DialogueDossier(Vector.empty, Vector.empty, Vector.empty))
object WitnessProfile:
  given Encoder[WitnessProfile] = Encoder.instance { p => Json.obj(
    "id" -> p.id.asJson, "point" -> p.point.asJson, "name" -> p.name.asJson,
    "background" -> p.background.asJson, "voice" -> p.voice.asJson, "answers" -> p.answers.asJson,
    "knowledge" -> p.dossier.knowledge.asJson, "privateContext" -> p.dossier.privateContext.asJson,
    "policy" -> p.dossier.policy.asJson)
  }
  given Decoder[WitnessProfile] = Decoder.instance { c =>
    for
      id <- c.get[WitnessId]("id")
      point <- c.get[PointId]("point")
      name <- c.get[String]("name")
      background <- c.get[String]("background")
      voice <- c.get[String]("voice")
      answers <- c.get[Vector[WitnessAnswer]]("answers")
      knowledge <- c.get[Option[Vector[String]]]("knowledge")
      privateContext <- c.get[Option[Vector[String]]]("privateContext")
      policy <- c.get[Option[Vector[String]]]("policy")
    yield WitnessProfile(id,point,name,background,voice,answers,
      DialogueDossier(knowledge.getOrElse(Vector.empty),privateContext.getOrElse(Vector.empty),policy.getOrElse(Vector.empty)))
  }
