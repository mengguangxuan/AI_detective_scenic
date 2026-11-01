package services.games.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.Sequence

final case class RecordedDialogueFocus(sequence: Sequence, focus: DialogueFocus)
object RecordedDialogueFocus:
  given Encoder[RecordedDialogueFocus] = deriveEncoder[RecordedDialogueFocus]
  given Decoder[RecordedDialogueFocus] = deriveDecoder[RecordedDialogueFocus]
