package services.accounts.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class LocalIdentityResponse(userId: UserId, token: String)
object LocalIdentityResponse:
  given Encoder[LocalIdentityResponse] = deriveEncoder[LocalIdentityResponse]
  given Decoder[LocalIdentityResponse] = deriveDecoder[LocalIdentityResponse]
