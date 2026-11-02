package services.games.api

import cats.effect.IO
import scala.concurrent.duration.*
import services.games.objects.*
import services.games.objects.apiTypes.CommandResult
import services.games.utils.{InterviewRules,SemanticInterviewRules}
import services.models.api.ChatCompletionClient
import services.system.api.DatabaseSession
import services.system.objects.*

// Workflow only: both model calls happen outside a connection and each write is fenced.
private[games] object ReviewInterviewMessage:
  def run(user: UserId,url: String,client: ChatCompletionClient,row: InterviewRequestRecord): IO[CommandResult] =
    def finish(decision: Option[InterviewDecision],failure: Option[InterviewReplyMode])=
      DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(
        FinishInterviewMessage(user,row.sessionId,row.requestId,row.leaseToken,decision,failure).plan(c))).flatMap(IO.fromEither)
    def save(draft: InterviewDecision,release: Boolean)=DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(
      SaveInterviewDraftMessage(user,row.sessionId,row.requestId,row.leaseToken,draft,release).plan(c)))
    val reply=row.draft.fold(client.complete(InterviewRules.prompt(row)).timeout(45.seconds).attempt.map {
      case Left(_) => Left(InterviewReplyMode.Unavailable)
      case Right(raw) => InterviewRules.decision(raw,row.context).toRight(InterviewReplyMode.Rejected)
    })(d => IO.pure(Right(d): Either[InterviewReplyMode,InterviewDecision]))
    reply.flatMap {
      case Left(mode) => finish(None,Some(mode))
      case Right(draft) =>
        for
          _ <- save(draft,false)
          reviewed <- client.complete(SemanticInterviewRules.reviewPrompt(row,draft)).timeout(35.seconds).attempt
          decision=reviewed.toOption.flatMap(SemanticInterviewRules.review(_,draft,row.context))
          _ <- reviewed match
            case Right(raw) if decision.isEmpty => IO.println("[interview] Recognition pending: " + SemanticInterviewRules.reviewSummary(raw))
            case Right(raw) if decision.flatMap(_.recognition).exists(_.unconfirmed) => IO.println("[interview] Recognition normalized: " + SemanticInterviewRules.reviewChecks(raw,draft,row.context))
            case _ => IO.unit
          result <- decision match
            case Some(value) => finish(Some(value),None)
            case None => save(draft,true) *> IO.raiseError[CommandResult](GameError(ErrorCode.RequestPending,
              "人物答复已保存，记录核对暂未完成。请用原请求重试；不会让人物重新回答。"))
        yield result
    }
