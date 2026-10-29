package services.games.api

import cats.effect.IO
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.objects.PoliceExchangeRecord
import services.games.objects.apiTypes.CommandResult
import services.games.tables.SessionTable.SessionTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.tables.PoliceExchangeTable.PoliceExchangeTable
import services.games.utils.{FixtureRules,MutationSupport}
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
            revision <- CaseRevisionTable.find(connection,session.caseId,session.caseRevision).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件修订不存在。")))
            plan <- IO.fromEither(FixtureRules.investigate(revision.content,session,requestId,point,route))
            presented = revision.content.routes.find(_.id == route).map(_.presentedClues).getOrElse(Set.empty)
            _ <- if revision.content.police.exists(_.point == point) && presented.nonEmpty then
              PoliceExchangeTable.insert(connection,PoliceExchangeRecord(sessionId,requestId,plan.session.sequence,presented,plan.record.text)) else IO.unit
            result <- MutationSupport.commit(connection,session,requestId,digest,plan)
          yield result
    yield result

object InvestigateMessage:
  given Encoder[InvestigateMessage] = deriveEncoder[InvestigateMessage]
  given Decoder[InvestigateMessage] = deriveDecoder[InvestigateMessage]
