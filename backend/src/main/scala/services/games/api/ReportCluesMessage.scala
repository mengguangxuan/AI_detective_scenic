package services.games.api

import cats.effect.IO
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.objects.*
import services.games.objects.apiTypes.CommandResult
import services.games.tables.SessionTable.SessionTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.tables.PoliceExchangeTable.PoliceExchangeTable
import services.games.utils.{FixtureRules,MutationSupport}
import services.system.api.APIMessage
import services.system.objects.*

final case class ReportCluesMessage(userId: UserId,sessionId: SessionId,requestId: RequestId,
  expectedSequence: Sequence,evidence: Set[ClueId]) extends APIMessage[CommandResult]:
  override def plan(connection: Connection): IO[CommandResult] =
    val digest=MutationSupport.reportDigest(userId,sessionId,requestId,expectedSequence,evidence)
    for
      _ <- IO.blocking(require(!connection.getAutoCommit,"Message requires transaction"))
      state <- SessionTable.lock(connection,sessionId).flatMap(MutationSupport.owned(_,userId))
      receipt <- ReceiptTable.find(connection,sessionId,requestId)
      result <- receipt match
        case Some(saved) => MutationSupport.retry(saved,digest)
        case None =>
          for
            _ <- MutationSupport.checkSequence(state,expectedSequence)
            _ <- IO.raiseUnless(evidence.nonEmpty && evidence.size <= 20)(GameError(ErrorCode.InvalidValue,"请选择1至20份已经取得的材料。"))
            _ <- IO.raiseUnless(evidence.subsetOf(state.clues))(GameError(ErrorCode.AccessDenied,"只能向警方出示已经取得的材料。"))
            revision <- CaseRevisionTable.find(connection,state.caseId,state.caseRevision).flatMap(value =>
              IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件修订不存在。")))
            contact <- IO.fromOption(revision.content.police)(GameError(ErrorCode.AccessDenied,"当前没有可接触的警方人员。"))
            _ <- IO.raiseUnless(revision.content.points.exists(p => p.id == contact.point && FixtureRules.accessible(revision.content,state,p)))(
              GameError(ErrorCode.AccessDenied,"当前无法与警方交流。"))
            previous <- PoliceExchangeTable.list(connection,sessionId)
            shared = previous.flatMap(_.evidence).toSet
            _ <- IO.raiseUnless((evidence -- shared).nonEmpty)(GameError(ErrorCode.ConditionFailed,"这些材料已经向警方出示过了。"))
            titles = revision.content.clues.filter(c => evidence(c.id)).map(_.title.value).mkString("、")
            reply = s"${contact.name}：收到了，$titles。我已把这几份材料记下。你可以继续检查现场，或向有关的人问清楚。"
            next = state.copy(sequence=Sequence(state.sequence.value+1))
            record = SessionRecord(RecordId(s"${requestId.value}:police"),sessionId,requestId,next.sequence,next.storyTimeSeconds,
              contact.point,Some(RouteId("police_exchange")),None,s"向警方出示材料：$titles\n$reply",Set.empty,Set.empty)
            result <- MutationSupport.commit(connection,state,requestId,digest,ActionPlan(next,record,
              CommandResult(next.sequence,Vector(Text(reply)),Set.empty)))
            _ <- PoliceExchangeTable.insert(connection,PoliceExchangeRecord(sessionId,requestId,next.sequence,evidence,reply))
          yield result
    yield result

object ReportCluesMessage:
  given Encoder[ReportCluesMessage] = deriveEncoder[ReportCluesMessage]
  given Decoder[ReportCluesMessage] = deriveDecoder[ReportCluesMessage]
