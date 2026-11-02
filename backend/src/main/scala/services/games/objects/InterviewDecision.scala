package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.WitnessAnswerId


final case class InterviewDecision(answerIds: Vector[WitnessAnswerId], reply: Option[String] = None,
  statementUpdates: Vector[InterviewStatementUpdate] = Vector.empty, focus: Option[DialogueFocus] = None, recognition: Option[SpeechRecognition] = None)
object InterviewDecision:
  given Encoder[InterviewDecision] = deriveEncoder[InterviewDecision].mapJson(json =>
    val semantic=if json.hcursor.get[Option[SpeechRecognition]]("recognition").contains(None) then json.mapObject(_.remove("recognition")) else json
    val focused=if json.hcursor.get[Option[DialogueFocus]]("focus").contains(None) then semantic.mapObject(_.remove("focus")) else semantic
    if json.hcursor.get[Vector[InterviewStatementUpdate]]("statementUpdates").contains(Vector.empty) then focused.mapObject(_.remove("statementUpdates")) else focused)
  given Decoder[InterviewDecision] = deriveDecoder[InterviewDecision].prepare(_.withFocus(_.mapObject(fields =>
    if fields.contains("statementUpdates") then fields else fields.add("statementUpdates",io.circe.Json.arr()))))
