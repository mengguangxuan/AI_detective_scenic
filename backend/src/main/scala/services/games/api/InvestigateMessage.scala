package services.games.api

import cats.effect.IO
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseTable.CaseTable
import services.games.objects.PoliceExchangeRecord
import services.games.objects.apiTypes.CommandResult
import services.games.tables.SessionTable.SessionTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.tables.PoliceExchangeTable.PoliceExchangeTable
import services.games.tables.SessionRecordTable.SessionRecordTable
import services.games.tables.InterviewTurnTable.InterviewTurnTable
import services.games.utils.{ContinuousInterviewRules, FixtureRules,MutationSupport, RecordedInterviewRules}
import services.system.api.APIMessage
import services.system.objects.*

final case class InvestigateMessage(userId: UserId,sessionId: SessionId,requestId: RequestId,expectedSequence: Sequence,point: PointId,route: RouteId) extends APIMessage[CommandResult]:
  override def plan(connection: Connection): IO[CommandResult] =
    val digest=MutationSupport.investigationDigest(userId,sessionId,requestId,expectedSequence,point,route)
    for
      _ <- IO.blocking(require(!connection.getAutoCommit,"Message requires transaction"))
      session <- SessionTable.lock(connection,sessionId).flatMap(MutationSupport.owned(_,userId))
      receipt <- ReceiptTable.find(connection,sessionId,requestId)
      result <- receipt match
        case Some(saved) => MutationSupport.retry(saved,digest)
        case None =>
          for
            _ <- MutationSupport.checkSequence(session,expectedSequence)
            caseRecord <- CaseTable.find(connection,session.caseId).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件不存在。")))
            records <- SessionRecordTable.list(connection,sessionId)
            turns <- InterviewTurnTable.list(connection,sessionId)
            content=RecordedInterviewRules.content(caseRecord.content,turns)
            continuous=content.witnesses.exists(w => w.point == point && (content.interviewPromptVersion >= 3 || content.continuousInterviews.exists(_.witness == w.id)))
            planned <- if continuous then IO.fromEither(ContinuousInterviewRules.fixed(content,session,requestId,point,route,records,turns)).map((action,turn) => (action,Some(turn)))
              else IO.fromEither(FixtureRules.investigate(content,session,requestId,point,route,records,turns)).map(action => (action,None))
            (plan,turn)=planned
            _ <- turn.fold(IO.unit)(InterviewTurnTable.insert(connection,_))
            presented = turn.map(_.shownClues).getOrElse(caseRecord.content.routes.find(_.id == route).map(_.presentedClues).getOrElse(Set.empty))
            _ <- if caseRecord.content.police.flatMap(id => caseRecord.content.witnesses.find(_.id == id)).exists(_.point == point) && presented.nonEmpty then
              PoliceExchangeTable.insert(connection,PoliceExchangeRecord(sessionId,requestId,plan.session.sequence,presented,plan.record.text)) else IO.unit
            result <- MutationSupport.commit(connection,session,requestId,digest,plan)
          yield result
    yield result

object InvestigateMessage:
  given Encoder[InvestigateMessage] = deriveEncoder[InvestigateMessage]
  given Decoder[InvestigateMessage] = deriveDecoder[InvestigateMessage]
