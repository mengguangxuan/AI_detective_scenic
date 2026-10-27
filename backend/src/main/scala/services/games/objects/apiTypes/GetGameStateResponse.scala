package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class GetGameStateResponse(sequence: Sequence, visiblePoints: Vector[PointId], clues: Set[ClueId], facts: Set[FactId])

object GetGameStateResponse:
  given Encoder[GetGameStateResponse] = deriveEncoder[GetGameStateResponse]
  given Decoder[GetGameStateResponse] = deriveDecoder[GetGameStateResponse]
