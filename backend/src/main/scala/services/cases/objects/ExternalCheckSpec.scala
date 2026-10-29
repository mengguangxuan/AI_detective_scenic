package services.cases.objects
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class ExternalCheckSpec(id: ExternalCheckId, title: String, requestLabel: String, point: PointId, requiredClues: Set[ClueId], delaySeconds: Int, resultClues: Set[ClueId])
object ExternalCheckSpec:
  given Encoder[ExternalCheckSpec] = deriveEncoder[ExternalCheckSpec]
  given Decoder[ExternalCheckSpec] = deriveDecoder[ExternalCheckSpec]
