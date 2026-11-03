package services.games.objects.apiTypes
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
final case class ClockTargetView(label: String, time: String)
object ClockTargetView:
  given Encoder[ClockTargetView] = deriveEncoder[ClockTargetView]
  given Decoder[ClockTargetView] = deriveDecoder[ClockTargetView]
final case class ClockView(label: String, elapsedSeconds: Long, actionMinutes: Int, maximumWaitMinutes: Int = 30,
  phaseEnd: Option[String] = None, targets: Vector[ClockTargetView] = Vector.empty)
object ClockView:
  given Encoder[ClockView] = deriveEncoder[ClockView]
  given Decoder[ClockView] = deriveDecoder[ClockView]
