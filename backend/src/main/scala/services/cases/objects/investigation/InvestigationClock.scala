package services.cases.objects.investigation
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class InvestigationClock(startMinutesOfDay: Int, actionSeconds: Int)
object InvestigationClock:
  given Encoder[InvestigationClock] = deriveEncoder[InvestigationClock]
  given Decoder[InvestigationClock] = deriveDecoder[InvestigationClock]
