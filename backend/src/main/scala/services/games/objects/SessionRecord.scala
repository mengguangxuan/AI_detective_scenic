package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*


final case class SessionRecord(id: RecordId, sessionId: SessionId, requestId: RequestId, sequence: Sequence,
  storyTimeSeconds: Long, point: PointId, route: Option[RouteId], operation: Option[OperationId],
  text: String, disclosedClues: Set[ClueId], disclosedFacts: Set[FactId])

object SessionRecord:
  given Encoder[SessionRecord] = deriveEncoder[SessionRecord]
  given Decoder[SessionRecord] = deriveDecoder[SessionRecord]
