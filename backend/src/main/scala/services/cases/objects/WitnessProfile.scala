package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*


final case class WitnessProfile(id: WitnessId, point: PointId, name: String, background: String, voice: String, answers: Vector[WitnessAnswer], knowledge: Vector[String] = Vector.empty,
  privateContext: Vector[String] = Vector.empty, policy: Vector[String] = Vector.empty)
object WitnessProfile:
  given Encoder[WitnessProfile] = deriveEncoder[WitnessProfile]
  given Decoder[WitnessProfile] = deriveDecoder[WitnessProfile].prepare(_.withFocus(_.mapObject(fields =>
    Vector("knowledge","privateContext","policy").foldLeft(fields)((obj,key) =>
      if obj.contains(key) then obj else obj.add(key,io.circe.Json.arr())))))
