package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.games.objects.{InterviewReplyMode,SpeechRecognition}
import services.system.objects.*
import services.cases.objects.{ClueId,WitnessId}

final case class InterviewTurnView(requestId: RequestId, sequence: Sequence, witness: WitnessId, question: String, shownClues: Set[ClueId], reply: String, mode: InterviewReplyMode, recognition: Option[SpeechRecognition] = None)
object InterviewTurnView:
  given Encoder[InterviewTurnView] = deriveEncoder[InterviewTurnView].mapJson(json => if json.hcursor.get[Option[SpeechRecognition]]("recognition").contains(None) then json.mapObject(_.remove("recognition")) else json)
  given Decoder[InterviewTurnView] = deriveDecoder[InterviewTurnView]
