package services.games.objects
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.ExternalCheckId
final case class ExternalCheckProgress(id: ExternalCheckId, requestedAtSeconds: Long, readyAtSeconds: Long, completedAtSeconds: Option[Long])
object ExternalCheckProgress:
  given Encoder[ExternalCheckProgress] = deriveEncoder[ExternalCheckProgress]
  given Decoder[ExternalCheckProgress] = deriveDecoder[ExternalCheckProgress]
