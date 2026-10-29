package services.games.objects

import io.circe.{Decoder, Encoder}

// Finite request/reply protocol states handled by the application.
enum InterviewRequestStatus:
  case Pending, Finished, Cancelled
object InterviewRequestStatus:
  def toString(value: InterviewRequestStatus): String = value.productPrefix.toLowerCase(java.util.Locale.ROOT)
  def fromString(value: String): Either[String, InterviewRequestStatus] =
    InterviewRequestStatus.values.find(toString(_) == value).toRight("Unsupported InterviewRequestStatus")
  given Encoder[InterviewRequestStatus] = Encoder.encodeString.contramap(toString)
  given Decoder[InterviewRequestStatus] = Decoder.decodeString.emap(fromString)
