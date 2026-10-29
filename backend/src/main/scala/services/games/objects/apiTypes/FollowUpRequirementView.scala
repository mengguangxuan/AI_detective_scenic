package services.games.objects.apiTypes
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class FollowUpRequirementView(label: String, met: Boolean)
object FollowUpRequirementView:
  given Encoder[FollowUpRequirementView] = deriveEncoder[FollowUpRequirementView]
  given Decoder[FollowUpRequirementView] = deriveDecoder[FollowUpRequirementView]
