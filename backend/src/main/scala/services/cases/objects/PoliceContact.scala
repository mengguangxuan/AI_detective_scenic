package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

final case class PoliceContact(point: PointId, name: String, background: String)
object PoliceContact:
  given Encoder[PoliceContact] = deriveEncoder[PoliceContact]
  given Decoder[PoliceContact] = deriveDecoder[PoliceContact]
