package services.games.objects.apiTypes

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.{OperationId,PointId,PlaceId}

final case class TravelChoiceView(operation: OperationId, point: PointId, label: String, destination: PlaceId, destinationName: String)
object TravelChoiceView:
  given Encoder[TravelChoiceView] = deriveEncoder[TravelChoiceView]
  given Decoder[TravelChoiceView] = deriveDecoder[TravelChoiceView]
