package services.cases.objects
import io.circe.{Decoder,Encoder}
final case class ExternalCheckId(value: String)
object ExternalCheckId:
  given Encoder[ExternalCheckId] = Encoder.encodeString.contramap(_.value)
  given Decoder[ExternalCheckId] = Decoder.decodeString.emap(s => Either.cond(s.matches("[a-z][a-z0-9_]{0,63}"),ExternalCheckId(s),"Invalid check ID"))
