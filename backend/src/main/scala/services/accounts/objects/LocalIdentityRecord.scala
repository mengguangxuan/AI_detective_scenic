package services.accounts.objects
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class LocalIdentityRecord(userId: UserId, tokenHash: String)
object LocalIdentityRecord:
  given Encoder[LocalIdentityRecord] = deriveEncoder[LocalIdentityRecord]
  given Decoder[LocalIdentityRecord] = deriveDecoder[LocalIdentityRecord]
