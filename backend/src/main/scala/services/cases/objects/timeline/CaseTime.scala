package services.cases.objects.timeline

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

// Signed seconds from the case's declared story origin, never a wall-clock timestamp.
final case class CaseTime(seconds: Long)
object CaseTime:
  given Encoder[CaseTime] = Encoder.encodeLong.contramap(_.seconds)
  given Decoder[CaseTime] = Decoder.decodeLong.map(CaseTime.apply)

final case class StoryDuration(seconds: Long)
object StoryDuration:
  given Encoder[StoryDuration] = Encoder.encodeLong.contramap(_.seconds)
  given Decoder[StoryDuration] = Decoder.decodeLong.emap(seconds =>
    Either.cond(seconds >= 0, StoryDuration(seconds), "Story duration must be nonnegative"))

// Inclusive bounds. None on one side means unknown, not an invented exact time.
// A completely unknown time belongs in the author draft, not an executable CaseEvent.
final case class TimeWindow(earliest: Option[CaseTime], latest: Option[CaseTime])
object TimeWindow:
  given Encoder[TimeWindow] = deriveEncoder[TimeWindow]
  given Decoder[TimeWindow] = deriveDecoder[TimeWindow].emap(window =>
    Either.cond(
      (window.earliest.nonEmpty || window.latest.nonEmpty) &&
        window.earliest.forall(start => window.latest.forall(_.seconds >= start.seconds)),
      window, "Time window requires at least one bound and earliest <= latest"))
