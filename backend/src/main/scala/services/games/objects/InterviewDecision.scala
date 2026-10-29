package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.WitnessAnswerId


final case class InterviewDecision(answerIds: Vector[WitnessAnswerId], reply: Option[String] = None)
object InterviewDecision:
  given Encoder[InterviewDecision] = deriveEncoder[InterviewDecision]
  given Decoder[InterviewDecision] = deriveDecoder[InterviewDecision]
