package services.games.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.ClueId

// Finite protocol dispositions, not an enumeration of motives or psychological states.
enum SpeechResponseKind:
  case Answer, Clarification, Refusal, Maintaining
object SpeechResponseKind:
  given Encoder[SpeechResponseKind] = Encoder.encodeString.contramap(_.productPrefix.toLowerCase(java.util.Locale.ROOT))
  given Decoder[SpeechResponseKind] = Decoder.decodeString.emap(value =>
    SpeechResponseKind.values.find(_.productPrefix.toLowerCase(java.util.Locale.ROOT) == value).toRight("Unknown speech response kind"))

// Quotes and actual delivered sources, never private model reasoning or objective truth.
final case class RecognizedClaim(clue: ClueId, text: String, evidence: Set[ClueId])
object RecognizedClaim:
  given Encoder[RecognizedClaim] = deriveEncoder[RecognizedClaim]
  given Decoder[RecognizedClaim] = deriveDecoder[RecognizedClaim]

final case class SpeechRecognition(kind: SpeechResponseKind, claims: Vector[RecognizedClaim], unconfirmed: Boolean = false)
object SpeechRecognition:
  given Encoder[SpeechRecognition] = deriveEncoder[SpeechRecognition].mapJson(json => if json.hcursor.get[Boolean]("unconfirmed").contains(false) then json.mapObject(_.remove("unconfirmed")) else json)
  given Decoder[SpeechRecognition] = deriveDecoder[SpeechRecognition].prepare(_.withFocus(_.mapObject(o => if o.contains("unconfirmed") then o else o.add("unconfirmed",io.circe.Json.False))))
