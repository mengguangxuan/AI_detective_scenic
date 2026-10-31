package services.games.routes
import services.games.api.*
import services.games.objects.apiTypes.*
import services.system.api.RegisteredAPIMessage
import services.models.api.ChatCompletionClient
import cats.effect.IO
import io.circe.syntax.*
import services.accounts.api.ResolveLocalIdentityMessage
import services.system.objects.{ErrorCode, GameError}
import services.system.objects.apiTypes.HttpReply
object GamesRoutes:
  val apiMessages: Vector[RegisteredAPIMessage] = Vector(
    RegisteredAPIMessage.transactional(RegisteredAPIMessage.nameOf[ListInvestigationsMessage],Set.empty) { (_,token,connection) =>
      for
        credential <- IO.fromOption(token)(GameError(ErrorCode.Unauthenticated,"请先建立本地演示身份。"))
        user <- ResolveLocalIdentityMessage(credential).plan(connection)
        result <- ListInvestigationsMessage(user).plan(connection)
      yield HttpReply(result.asJson)
    },
    RegisteredAPIMessage.authenticated[OpenInvestigationMessage,OpenInvestigationInput,InvestigationSnapshot](Set("sessionId","caseId","revision"))((input,user) => OpenInvestigationMessage(user,input.sessionId,input.caseId,input.revision)),
    RegisteredAPIMessage.authenticated[RevealReasoningMessage,RevealReasoningInput,CommandResult](Set("sessionId","requestId","expectedSequence","abandon"))((input,user) => RevealReasoningMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.abandon)),
    RegisteredAPIMessage.authenticated[SubmitConclusionMessage,SubmitConclusionInput,CommandResult](Set("sessionId","requestId","expectedSequence","suspects","evidence","explanation"))((input,user) => SubmitConclusionMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.suspects,input.evidence,input.explanation)).copy(longTextFields=Set("explanation")),
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
    RegisteredAPIMessage.authenticatedWorkflow[EvaluateReasoningMessage,EvaluateReasoningInput,CommandResult](
      Set("sessionId","requestId","expectedSequence","suspects","evidence","explanation"),Set("explanation")) { (input,user,url) =>
      EvaluateReasoningMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.suspects,input.evidence,input.explanation).run(url,client)
    },
    RegisteredAPIMessage.authenticatedWorkflow[AskWitnessMessage,AskWitnessInput,CommandResult](
      Set("sessionId","requestId","expectedSequence","witness","question","evidence"),Set("question")) { (input,user,url) =>
      AskWitnessMessage(user,input.sessionId,input.requestId,input.expectedSequence,input.witness,input.question,input.evidence).run(url,client)
    }
  )
