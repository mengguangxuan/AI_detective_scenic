package services.system.api

import cats.effect.IO
import io.circe.{Decoder,Encoder,Json}
import io.circe.syntax.*
import java.sql.Connection
import scala.reflect.ClassTag
import services.accounts.api.ResolveLocalIdentityMessage
import services.system.objects.*
import services.system.objects.apiTypes.HttpReply

// Every route declares its transaction boundary. Long-running workflows own multiple short transactions.
final case class RegisteredAPIMessage(name: String,fields: Set[String],
  run: (Json,Option[String],String) => IO[HttpReply],longTextFields: Set[String] = Set.empty)
object RegisteredAPIMessage:
  def nameOf[M: ClassTag]: String =
    summon[ClassTag[M]].runtimeClass.getSimpleName.stripSuffix("Message").toLowerCase(java.util.Locale.ROOT)

  def transactional(name: String,fields: Set[String])(run: (Json,Option[String],Connection) => IO[HttpReply]): RegisteredAPIMessage =
    RegisteredAPIMessage(name,fields,(payload,token,url) =>
      DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(run(payload,token,c))))

  def authenticated[M <: APIMessage[R]: ClassTag,I: Decoder,R: Encoder](fields: Set[String])(build: (I,UserId) => M): RegisteredAPIMessage =
    transactional(nameOf[M],fields) { (payload,token,connection) =>
      for
        credential <- IO.fromOption(token)(GameError(ErrorCode.Unauthenticated,"请先建立本地演示身份。"))
        user <- ResolveLocalIdentityMessage(credential).plan(connection)
        input <- IO.fromEither(payload.as[I].left.map(_ => GameError(ErrorCode.InvalidValue,"请求字段格式不正确。")))
        result <- build(input,user).plan(connection)
      yield HttpReply(result.asJson)
    }

  def authenticatedWorkflow[M: ClassTag,I: Decoder,R: Encoder](fields: Set[String],longTextFields: Set[String])(
      run: (I,UserId,String) => IO[R]): RegisteredAPIMessage =
    RegisteredAPIMessage(nameOf[M],fields,(payload,token,url) =>
      for
        credential <- IO.fromOption(token)(GameError(ErrorCode.Unauthenticated,"请先建立本地演示身份。"))
        input <- IO.fromEither(payload.as[I].left.map(_ => GameError(ErrorCode.InvalidValue,"请求字段格式不正确。")))
        user <- DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(ResolveLocalIdentityMessage(credential).plan(c)))
        result <- run(input,user,url)
      yield HttpReply(result.asJson),longTextFields)
