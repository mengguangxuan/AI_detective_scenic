package services.accounts.api
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import cats.effect.IO
import java.sql.Connection
import java.security.SecureRandom
import java.util.UUID
import services.system.api.APIMessage
import services.system.objects.*
import services.accounts.objects.LocalIdentityRecord
import services.accounts.objects.apiTypes.LocalIdentityResponse
import services.accounts.tables.LocalIdentityTable.LocalIdentityTable
import services.accounts.utils.LocalTokens
// Local demo only: no password/account registration or production authentication claims.
final case class OpenLocalIdentityMessage(existingToken: Option[String]) extends APIMessage[LocalIdentityResponse]:
  override def plan(connection: Connection): IO[LocalIdentityResponse] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit))
      existing <- existingToken.filter(LocalTokens.valid) match
        case Some(token) => LocalIdentityTable.findUser(connection,LocalTokens.hash(token)).map(_.map(id => LocalIdentityResponse(id,token)))
        case None => IO.pure(None)
      result <- existing match
        case Some(identity) => IO.pure(identity)
        case None =>
          for
            token <- IO.delay {
              val bytes = new Array[Byte](32)
              new SecureRandom().nextBytes(bytes)
              bytes.map(b => f"${b & 0xff}%02x").mkString
            }
            user <- IO.delay(UserId(UUID.randomUUID().toString))
            _ <- LocalIdentityTable.insert(connection,LocalIdentityRecord(user,LocalTokens.hash(token)))
          yield LocalIdentityResponse(user,token)
    yield result

object OpenLocalIdentityMessage:
  given Encoder[OpenLocalIdentityMessage] = deriveEncoder[OpenLocalIdentityMessage]
  given Decoder[OpenLocalIdentityMessage] = deriveDecoder[OpenLocalIdentityMessage]
