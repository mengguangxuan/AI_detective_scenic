package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class InvestigateInput(sessionId: SessionId, requestId: RequestId, expectedSequence: Sequence, point: PointId, route: RouteId)
object InvestigateInput:
  given Encoder[InvestigateInput] = deriveEncoder[InvestigateInput]
  given Decoder[InvestigateInput] = deriveDecoder[InvestigateInput]
