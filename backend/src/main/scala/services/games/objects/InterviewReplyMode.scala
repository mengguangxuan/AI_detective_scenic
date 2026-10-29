package services.games.objects

import io.circe.{Decoder, Encoder}

// Finite request/reply protocol states handled by the application.
enum InterviewReplyMode:
  case Answered, Unknown, Unavailable, Rejected
object InterviewReplyMode:
  def toString(value: InterviewReplyMode): String = value.productPrefix.toLowerCase(java.util.Locale.ROOT)
  def fromString(value: String): Either[String, InterviewReplyMode] =
    InterviewReplyMode.values.find(toString(_) == value).toRight("Unsupported InterviewReplyMode")
  given Encoder[InterviewReplyMode] = Encoder.encodeString.contramap(toString)
  given Decoder[InterviewReplyMode] = Decoder.decodeString.emap(fromString)
