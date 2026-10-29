package services.cases.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class DialogueDossier(knowledge: Vector[String], privateContext: Vector[String], policy: Vector[String])
object DialogueDossier:
  given Encoder[DialogueDossier] = deriveEncoder[DialogueDossier]
  given Decoder[DialogueDossier] = deriveDecoder[DialogueDossier]
