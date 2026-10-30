package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

// Compiled police projection. The author selects a WitnessInteraction by ID.
final case class PoliceContact(point: PointId, name: String, background: String, id: WitnessId,
  voice: String, dossier: DialogueDossier, answers: Vector[WitnessAnswer])
object PoliceContact:
  given Encoder[PoliceContact] = deriveEncoder[PoliceContact]
  given Decoder[PoliceContact] = Decoder.instance { c =>
    for
      point <- c.get[PointId]("point")
      name <- c.get[String]("name")
      background <- c.get[String]("background")
      id <- c.get[Option[WitnessId]]("id")
      voice <- c.get[Option[String]]("voice")
      dossier <- c.get[Option[DialogueDossier]]("dossier")
      answers <- c.get[Option[Vector[WitnessAnswer]]]("answers")
    // Only old stored police projections omit these fields.
    yield PoliceContact(point,name,background,id.getOrElse(WitnessId("melchett")),
      voice.getOrElse("直率、务实，清楚区分供述、医生意见和待核实的猜测。"),
      dossier.getOrElse(DialogueDossier(Vector.empty,Vector.empty,Vector.empty)),answers.getOrElse(Vector.empty))
  }
