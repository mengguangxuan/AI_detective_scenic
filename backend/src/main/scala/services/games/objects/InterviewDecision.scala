package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.WitnessAnswerId


final case class InterviewDecision(answerIds: Vector[WitnessAnswerId], reply: Option[String] = None,
  statementUpdates: Vector[InterviewStatementUpdate] = Vector.empty)
object InterviewDecision:
  given Encoder[InterviewDecision] = deriveEncoder[InterviewDecision].mapJson(json =>
    if json.hcursor.get[Vector[InterviewStatementUpdate]]("statementUpdates").contains(Vector.empty) then json.mapObject(_.remove("statementUpdates")) else json)
  given Decoder[InterviewDecision] = deriveDecoder[InterviewDecision].prepare(_.withFocus(_.mapObject(fields =>
    if fields.contains("statementUpdates") then fields else fields.add("statementUpdates",io.circe.Json.arr()))))
