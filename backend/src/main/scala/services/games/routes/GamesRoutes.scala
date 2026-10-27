package services.games.routes
import services.games.api.*
import services.games.objects.apiTypes.*
import services.system.api.RegisteredAPIMessage
object GamesRoutes:
  val apiMessages: Vector[RegisteredAPIMessage] = Vector(
    RegisteredAPIMessage.authenticated[OpenStudyMessage,SessionInput,InvestigationSnapshot](Set("sessionId"))((input,user) => OpenStudyMessage(user,input.sessionId)),
    RegisteredAPIMessage.authenticated[GetInvestigationSnapshotMessage,SessionInput,InvestigationSnapshot](Set("sessionId"))((input,user) => GetInvestigationSnapshotMessage(user,input.sessionId)),
    RegisteredAPIMessage.authenticated[ExecuteOperationMessage,ExecuteOperationInput,CommandResult](Set("sessionId","requestId","expectedSequence","operation"))((input,user) => ExecuteOperationMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.operation)),
    RegisteredAPIMessage.authenticated[InvestigateMessage,InvestigateInput,CommandResult](Set("sessionId","requestId","expectedSequence","point","route"))((input,user) => InvestigateMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.point,input.route))
  )
