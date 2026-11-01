package services.games.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

// Conversational metadata only; quotes must come from the saved reply, never grant evidence.
final case class DialogueFocus(topic: String, pendingQuestion: Option[String], clarification: Boolean)
object DialogueFocus:
  given Encoder[DialogueFocus] = deriveEncoder[DialogueFocus]
  given Decoder[DialogueFocus] = deriveDecoder[DialogueFocus]
