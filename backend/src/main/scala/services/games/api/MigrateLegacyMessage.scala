package services.games.api

import cats.effect.IO
import cats.syntax.all.*
import java.nio.file.Path
import java.sql.Connection
import services.cases.api.ImportLegacyCaseMessage
import services.games.objects.*
import services.games.objects.migration.*
import services.games.tables.SessionTable.SessionTable
import services.games.tables.SessionRecordTable.SessionRecordTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.tables.migration.LegacySessionTable.LegacySessionTable
import services.games.tables.migration.LegacyCommitTable.LegacyCommitTable
import services.games.tables.migration.LegacyReceiptTable.LegacyReceiptTable
import services.games.utils.{FixtureRules,LegacyMigrationRules,MutationSupport}
import services.system.api.{APIMessage,DatabaseSession}
import services.system.objects.*

// Offline migration only; source must be a read-only existing database.
final case class MigrateLegacyMessage(sourceUrl: String,caseSource: Path) extends APIMessage[MigrationReport]:
  override def plan(connection: Connection): IO[MigrationReport] =
    for
      _ <- IO.blocking(require(!connection.getAutoCommit && sourceUrl.contains("ACCESS_MODE_DATA=r") && sourceUrl.contains("IFEXISTS=TRUE")))
      report <- DatabaseSession.connection(sourceUrl).use { source =>
        for
          sessions <- LegacySessionTable.list(source)
          reports <- sessions.traverse { old => for
            revision <- ImportLegacyCaseMessage(caseSource,old.fixture).plan(connection)
            commits <- LegacyCommitTable.list(source,old.id)
            plans <- IO.fromEither(LegacyMigrationRules.convert(old,commits,revision))
            receipts <- LegacyReceiptTable.list(source,old.id)
            _ <- IO.raiseUnless(receipts.size==plans.size && receipts.forall(r => plans.exists(p => p.record.requestId==r.requestId && p.result==r.result)))(GameError(ErrorCode.InvalidValue,"旧请求回执与调查记录不一致。"))
            _ <- receipts.traverse_ { r =>
              val action = plans.find(_.record.requestId == r.requestId).get.record
              val expected = Sequence(action.sequence.value - 1)
              val digest = action.operation match
                case Some(operation) => MutationSupport.operationDigest(old.owner, old.id, r.requestId, expected, operation)
                case None => MutationSupport.investigationDigest(old.owner, old.id, r.requestId, expected, action.point, action.route.get)
              IO.raiseUnless(r.digest == digest)(GameError(ErrorCode.InvalidValue, "旧请求摘要不兼容，迁移已停止。"))
            }
            finalState=plans.lastOption.map(_.session).getOrElse(FixtureRules.initial(revision,old.id,old.owner))
            existing <- SessionTable.find(connection,old.id)
            _ <- existing match
              case Some(saved) =>
                for
                  _ <- IO.raiseUnless(saved==finalState)(GameError(ErrorCode.ConflictingContent,"目标会话已有不同状态。"))
                  records <- SessionRecordTable.list(connection,old.id)
                  _ <- IO.raiseUnless(records==plans.map(_.record))(GameError(ErrorCode.ConflictingContent,"目标调查记录不一致。"))
                  _ <- receipts.traverse_ { r => ReceiptTable.find(connection,old.id,r.requestId).flatMap { saved =>
                    IO.raiseUnless(saved.contains(RequestReceiptRecord(old.id,r.requestId,r.digest,1,r.result)))(GameError(ErrorCode.ConflictingContent,"目标请求回执不一致。"))
                  }}
                yield ()
              case None =>
                for
                  _ <- SessionTable.insert(connection,finalState)
                  _ <- plans.traverse_(p => SessionRecordTable.insert(connection,p.record))
                  _ <- receipts.traverse_(r => ReceiptTable.insert(connection,RequestReceiptRecord(old.id,r.requestId,r.digest,1,r.result)))
                yield ()
          yield MigrationReport(1,plans.size,receipts.size) }
        yield MigrationReport(reports.map(_.sessions).sum,reports.map(_.records).sum,reports.map(_.receipts).sum)
      }
    yield report
