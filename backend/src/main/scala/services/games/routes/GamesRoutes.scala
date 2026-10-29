package services.games.routes
import services.games.api.*
import services.games.objects.apiTypes.*
import services.system.api.RegisteredAPIMessage
import services.models.api.ChatCompletionClient
object GamesRoutes:
  val apiMessages: Vector[RegisteredAPIMessage] = Vector(
    RegisteredAPIMessage.authenticated[RequestExternalCheckMessage,RequestExternalCheckInput,CommandResult](Set("sessionId","requestId","expectedSequence","check"))((input,user) => RequestExternalCheckMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.check)),
    RegisteredAPIMessage.authenticated[AdvanceTimeMessage,AdvanceTimeInput,CommandResult](Set("sessionId","requestId","expectedSequence","minutes"))((input,user) => AdvanceTimeMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.minutes)),
    RegisteredAPIMessage.authenticated[ReportCluesMessage,ReportCluesInput,CommandResult](Set("sessionId","requestId","expectedSequence","evidence"))((input,user) => ReportCluesMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.evidence)),
    RegisteredAPIMessage.authenticated[OpenStudyMessage,SessionInput,InvestigationSnapshot](Set("sessionId"))((input,user) => OpenStudyMessage(user,input.sessionId)),
    RegisteredAPIMessage.authenticated[GetInvestigationSnapshotMessage,SessionInput,InvestigationSnapshot](Set("sessionId"))((input,user) => GetInvestigationSnapshotMessage(user,input.sessionId)),
    RegisteredAPIMessage.authenticated[ExecuteOperationMessage,ExecuteOperationInput,CommandResult](Set("sessionId","requestId","expectedSequence","operation"))((input,user) => ExecuteOperationMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.operation)),
    RegisteredAPIMessage.authenticated[InvestigateMessage,InvestigateInput,CommandResult](Set("sessionId","requestId","expectedSequence","point","route"))((input,user) => InvestigateMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.point,input.route)),
    RegisteredAPIMessage.authenticated[CompareTimesMessage,CompareTimesInput,CommandResult](Set("sessionId","requestId","expectedSequence","evidence","convertedTimeMinutes","assessment"))((input,user) => CompareTimesMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.evidence,input.convertedTimeMinutes,input.assessment))
  )

  def interviewMessages(client: ChatCompletionClient): Vector[RegisteredAPIMessage] = Vector(
    RegisteredAPIMessage.authenticatedWorkflow[AskWitnessMessage,AskWitnessInput,CommandResult](
      Set("sessionId","requestId","expectedSequence","witness","question","evidence"),Set("question")) { (input,user,url) =>
      AskWitnessMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.witness,input.question,input.evidence).run(url,client)
    }
  )
