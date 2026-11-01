package services.models.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*


final case class ChatCompletionInput(system: String, user: String, history: Vector[ChatCompletionExchange] = Vector.empty)
object ChatCompletionInput:
  given Encoder[ChatCompletionInput] = deriveEncoder[ChatCompletionInput].mapJson(json =>
    if json.hcursor.get[Vector[ChatCompletionExchange]]("history").contains(Vector.empty) then json.mapObject(_.remove("history")) else json)
  given Decoder[ChatCompletionInput] = deriveDecoder[ChatCompletionInput].prepare(_.withFocus(_.mapObject(fields =>
    if fields.contains("history") then fields else fields.add("history",io.circe.Json.arr()))))
