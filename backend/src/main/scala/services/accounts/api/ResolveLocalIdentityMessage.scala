package services.accounts.api
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import cats.effect.IO
import java.sql.Connection
import services.system.api.APIMessage
import services.system.objects.*
import services.accounts.tables.LocalIdentityTable.LocalIdentityTable
import services.accounts.utils.LocalTokens
final case class ResolveLocalIdentityMessage(token: String) extends APIMessage[UserId]:
  override def plan(connection: Connection): IO[UserId] =
    if !LocalTokens.valid(token) then IO.raiseError(GameError(ErrorCode.Unauthenticated,"本地演示凭证无效，请重新连接。"))
    else LocalIdentityTable.findUser(connection,LocalTokens.hash(token)).flatMap(value =>
      IO.fromOption(value)(GameError(ErrorCode.Unauthenticated,"本地演示凭证无效，请重新连接。")))

object ResolveLocalIdentityMessage:
  given Encoder[ResolveLocalIdentityMessage] = deriveEncoder[ResolveLocalIdentityMessage]
  given Decoder[ResolveLocalIdentityMessage] = deriveDecoder[ResolveLocalIdentityMessage]
