package services.games.api

import cats.effect.IO
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import java.util.UUID
import scala.concurrent.duration.*
import services.cases.objects.*
import services.cases.api.GetDialogueKnowledgeMessage
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.objects.*
import services.games.objects.apiTypes.CommandResult
import services.games.tables.SessionTable.SessionTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.tables.InterviewRequestTable.InterviewRequestTable
import services.games.tables.InterviewTurnTable.InterviewTurnTable
import services.games.tables.SessionRecordTable.SessionRecordTable
import services.games.tables.PoliceExchangeTable.PoliceExchangeTable
import services.games.utils.{InterviewRules,MutationSupport}
import services.models.api.ChatCompletionClient
import services.system.api.{APIMessage,DatabaseSession}
import services.system.objects.*

final case class AskWitnessMessage(userId: UserId,sessionId: SessionId,requestId: RequestId,expectedSequence: Sequence,
  witness: WitnessId,question: String,evidence: Set[ClueId]) extends APIMessage[InterviewPreparation]:
  override def plan(connection: Connection): IO[InterviewPreparation] =
    val digest=MutationSupport.interviewDigest(userId,sessionId,requestId,expectedSequence,witness,question,evidence)
    for
      _ <- IO.blocking(require(!connection.getAutoCommit,"Message requires transaction"))
      session <- SessionTable.lock(connection,sessionId).flatMap(MutationSupport.owned(_,userId))
      receipt <- ReceiptTable.find(connection,sessionId,requestId)
      prepared <- receipt match
        case Some(saved) => MutationSupport.retry(saved,digest).map(InterviewPreparation.Cached.apply)
        case None =>
          for
            now <- IO.realTime.map(_.toMillis)
            existing <- InterviewRequestTable.find(connection,sessionId,requestId)
            prepared <- existing match
              case Some(row) if row.digest != digest => IO.raiseError(GameError(ErrorCode.RequestConflict,"同一请求编号的内容发生变化。"))
              case Some(row) if row.status != InterviewRequestStatus.Pending || row.expectedSequence != session.sequence =>
                InterviewRequestTable.replace(connection,row.copy(status=InterviewRequestStatus.Cancelled),row.leaseToken)
                  .as(InterviewPreparation.Rejected(GameError(ErrorCode.StaleState,"调查已有变化，请重新提出问题。")))
              case Some(row) if row.leaseUntilMillis > now => IO.raiseError(GameError(ErrorCode.RequestPending,"这次问询仍在处理中，请稍后用原请求重试。"))
              case Some(row) =>
                for
                  token <- IO(UUID.randomUUID().toString)
                  renewed = row.copy(leaseToken=token,leaseUntilMillis=now+90000)
                  _ <- InterviewRequestTable.replace(connection,renewed,row.leaseToken)
                yield InterviewPreparation.Reserved(renewed)
              case None =>
                for
                  _ <- MutationSupport.checkSequence(session,expectedSequence)
                  active <- InterviewRequestTable.hasActive(connection,sessionId,now)
                  _ <- IO.raiseWhen(active)(GameError(ErrorCode.RequestPending,"这场调查还有问询正在处理中，请稍后重试。"))
                  revision <- CaseRevisionTable.find(connection,session.caseId,session.caseRevision).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件修订不存在。")))
                  turns <- InterviewTurnTable.list(connection,sessionId)
                  records <- SessionRecordTable.list(connection,sessionId)
                  exchanges <- PoliceExchangeTable.list(connection,sessionId)
                  dossier <- GetDialogueKnowledgeMessage(session.caseId,session.caseRevision,witness).plan(connection)
                  context <- IO.fromEither(InterviewRules.context(revision.content,session,witness,question,evidence,turns,records,dossier.knowledge,exchanges,dossier.privateContext,dossier.policy))
                  token <- IO(UUID.randomUUID().toString)
                  row = InterviewRequestRecord(sessionId,requestId,digest,expectedSequence,witness,question,evidence,context,token,now+90000,InterviewRequestStatus.Pending)
                  _ <- InterviewRequestTable.insert(connection,row)
                yield InterviewPreparation.Reserved(row)
          yield prepared
    yield prepared

  // This extra entry owns a real resource boundary: no JDBC connection exists during the outbound call.
  def run(databaseUrl: String,client: ChatCompletionClient): IO[CommandResult] =
    DatabaseSession.connection(databaseUrl).use(c => DatabaseSession.transaction(c)(plan(c))).flatMap {
      case InterviewPreparation.Cached(result) => IO.pure(result)
      case InterviewPreparation.Rejected(error) => IO.raiseError(error)
      case InterviewPreparation.Reserved(row) =>
        for
          completion <- client.complete(InterviewRules.prompt(row)).timeout(65.seconds).attempt
          decision = completion.toOption.flatMap(raw => InterviewRules.decision(raw,row.context))
          failure = completion match
            case Left(_) => Some(InterviewReplyMode.Unavailable)
            case Right(_) if decision.isEmpty => Some(InterviewReplyMode.Rejected)
            case _ => None
          outcome <- DatabaseSession.connection(databaseUrl).use(c => DatabaseSession.transaction(c)(
            FinishInterviewMessage(userId,sessionId,requestId,row.leaseToken,decision,failure).plan(c)))
          result <- IO.fromEither(outcome)
        yield result
    }
object AskWitnessMessage:
  given Encoder[AskWitnessMessage] = deriveEncoder[AskWitnessMessage]
  given Decoder[AskWitnessMessage] = deriveDecoder[AskWitnessMessage]
