package services.cases.objects.investigation

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.{ClueId, RouteId, WitnessAnswerId}


final case class WitnessAnswer(id: WitnessAnswerId, text: String, routes: Vector[RouteId], requiredEvidence: Set[ClueId])
object WitnessAnswer:
  given Encoder[WitnessAnswer] = deriveEncoder[WitnessAnswer]
  given Decoder[WitnessAnswer] = deriveDecoder[WitnessAnswer]
