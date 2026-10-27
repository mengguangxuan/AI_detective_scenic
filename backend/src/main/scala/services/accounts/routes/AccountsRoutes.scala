package services.accounts.routes
import io.circe.Json
import services.accounts.api.OpenLocalIdentityMessage
import services.system.api.RegisteredAPIMessage
import services.system.objects.apiTypes.HttpReply
object AccountsRoutes:
  val apiMessages: Vector[RegisteredAPIMessage] = Vector(
    RegisteredAPIMessage(RegisteredAPIMessage.nameOf[OpenLocalIdentityMessage],Set.empty,(_,token,connection) =>
      OpenLocalIdentityMessage(token).plan(connection).map(identity =>
        HttpReply(Json.obj("mode" -> Json.fromString("local-demo")),Some(identity.token))))
  )
