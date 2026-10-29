package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.games.objects.apiTypes.CommandResult
import services.system.objects.GameError

enum InterviewPreparation:
  case Cached(result: CommandResult)
  case Rejected(error: GameError)
  case Reserved(request: InterviewRequestRecord)
object InterviewPreparation:
  given Encoder[InterviewPreparation] = deriveEncoder[InterviewPreparation]
  given Decoder[InterviewPreparation] = deriveDecoder[InterviewPreparation]
