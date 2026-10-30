package services.cases.objects.investigation

import io.circe.{Decoder, Encoder, DecodingFailure}
import io.circe.generic.semiauto.*
import services.cases.objects.{PersonId, PointId, WitnessId}

final case class WitnessInteraction(id: WitnessId, person: PersonId, point: PointId, answers: Vector[WitnessAnswer])
object WitnessInteraction:
  given Encoder[WitnessInteraction] = deriveEncoder[WitnessInteraction]
  private val decoder: Decoder[WitnessInteraction] = deriveDecoder[WitnessInteraction]
  given Decoder[WitnessInteraction] = Decoder.instance { cursor =>
    if cursor.value.asObject.exists(_.keys.toSet == Set("id", "person", "point", "answers")) then decoder(cursor)
    else Left(DecodingFailure("问询绑定字段缺失或重复包含人物资料", cursor.history))
  }
