package services.games.api

import cats.effect.IO
import java.sql.Connection
import services.games.objects.*
import services.games.tables.SessionTable.SessionTable
import services.games.tables.InterviewRequestTable.InterviewRequestTable
import services.games.utils.{InterviewRules,MutationSupport}
import services.system.api.APIMessage
import services.system.objects.*
import io.circe.Json
import io.circe.syntax.*

// Internal checkpoint. A failed semantic review never makes the character say a second answer.
final case class SaveInterviewDraftMessage(userId: UserId,sessionId: SessionId,requestId: RequestId,token: String,
  draft: InterviewDecision, release: Boolean) extends APIMessage[Unit]:
  override def plan(connection: Connection): IO[Unit] =
    for
      state <- SessionTable.lock(connection,sessionId).flatMap(MutationSupport.owned(_,userId))
      row <- InterviewRequestTable.find(connection,sessionId,requestId).flatMap(r => IO.fromOption(r)(GameError(ErrorCode.InvalidValue,"问询请求不存在。")))
      now <- IO.realTime.map(_.toMillis)
      _ <- IO.raiseUnless(row.context.responseVersion == 5 && row.status == InterviewRequestStatus.Pending && row.leaseToken == token && row.leaseUntilMillis > now && state.sequence == row.expectedSequence)(GameError(ErrorCode.StaleState,"调查已有变化，不能保存这份答复。"))
      checked=InterviewRules.decision(Json.obj("reply" -> draft.reply.asJson,"focus" -> draft.focus.asJson).noSpaces,row.context)
      _ <- IO.raiseUnless(checked.contains(draft) && row.draft.forall(_ == draft))(GameError(ErrorCode.InvalidValue,"待核对答复不合法或已发生变化。"))
      _ <- InterviewRequestTable.replace(connection,row.copy(draft=Some(draft),leaseUntilMillis=if release then 0L else now+90000),token)
    yield ()
