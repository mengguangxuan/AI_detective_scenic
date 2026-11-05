package services.models.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*


final case class ChatCompletionInput(system: String, user: String, history: Vector[ChatCompletionExchange] = Vector.empty, images: Vector[ChatCompletionImage] = Vector.empty)
object ChatCompletionInput:
  given Encoder[ChatCompletionInput] = deriveEncoder[ChatCompletionInput].mapJson(json =>
    val history=if json.hcursor.get[Vector[ChatCompletionExchange]]("history").contains(Vector.empty) then json.mapObject(_.remove("history")) else json
    if json.hcursor.get[Vector[ChatCompletionImage]]("images").contains(Vector.empty) then history.mapObject(_.remove("images")) else history)
  given Decoder[ChatCompletionInput] = deriveDecoder[ChatCompletionInput].prepare { cursor =>
    cursor.withFocus(_.mapObject { fields =>
      Vector("history","images").foldLeft(fields) { (current,key) =>
        if current.contains(key) then current else current.add(key,io.circe.Json.arr())
      }
    })
  }
