package services.cases.routes

import io.circe.syntax.*
import services.cases.api.GetCaseCatalogMessage
import services.system.api.RegisteredAPIMessage
import services.system.objects.apiTypes.HttpReply

object CasesRoutes:
  val apiMessages: Vector[RegisteredAPIMessage] = Vector(
    RegisteredAPIMessage.transactional(RegisteredAPIMessage.nameOf[GetCaseCatalogMessage],Set.empty) { (_,_,connection) =>
      GetCaseCatalogMessage().plan(connection).map(result => HttpReply(result.asJson))
    })
