package services.games.objects.apiTypes
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class ClockView(label: String, elapsedSeconds: Long, actionMinutes: Int)
object ClockView:
  given Encoder[ClockView] = deriveEncoder[ClockView]
  given Decoder[ClockView] = deriveDecoder[ClockView]
