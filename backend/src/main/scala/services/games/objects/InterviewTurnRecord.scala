package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class InterviewTurnRecord(sessionId: SessionId, requestId: RequestId, sequence: Sequence, witness: WitnessId, question: String, shownClues: Set[ClueId], reply: String, mode: InterviewReplyMode, answerIds: Vector[WitnessAnswerId],
  statementUpdates: Vector[InterviewStatementUpdate] = Vector.empty, focus: Option[DialogueFocus] = None)
object InterviewTurnRecord:
  given Encoder[InterviewTurnRecord] = deriveEncoder[InterviewTurnRecord].mapJson(json =>
    val focused=if json.hcursor.get[Option[DialogueFocus]]("focus").contains(None) then json.mapObject(_.remove("focus")) else json
    if json.hcursor.get[Vector[InterviewStatementUpdate]]("statementUpdates").contains(Vector.empty) then focused.mapObject(_.remove("statementUpdates")) else focused)
  given Decoder[InterviewTurnRecord] = deriveDecoder[InterviewTurnRecord].prepare(_.withFocus(_.mapObject(fields =>
    if fields.contains("statementUpdates") then fields else fields.add("statementUpdates",io.circe.Json.arr()))))
