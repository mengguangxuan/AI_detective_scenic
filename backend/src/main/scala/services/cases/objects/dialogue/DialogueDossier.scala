package services.cases.objects.dialogue

import io.circe.{Decoder,Encoder,DecodingFailure}
import io.circe.generic.semiauto.*

final case class DialogueDossier(knowledge: Vector[String], privateContext: Vector[String], policy: Vector[String])
object DialogueDossier:
  given Encoder[DialogueDossier] = deriveEncoder[DialogueDossier]
  private val decoder: Decoder[DialogueDossier] = deriveDecoder[DialogueDossier]
  given Decoder[DialogueDossier] = Decoder.instance { cursor =>
    if cursor.value.asObject.exists(_.keys.toSet == Set("knowledge","privateContext","policy")) then decoder(cursor)
    else Left(DecodingFailure("人物主观资料字段缺失或包含未知字段",cursor.history))
  }
