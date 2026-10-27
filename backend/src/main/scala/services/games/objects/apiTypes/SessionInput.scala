package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class SessionInput(sessionId: SessionId)
object SessionInput:
  given Encoder[SessionInput] = deriveEncoder[SessionInput]
  given Decoder[SessionInput] = deriveDecoder[SessionInput]
