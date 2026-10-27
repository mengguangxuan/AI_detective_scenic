package services.system.api

import cats.effect.{IO, IOApp}
import io.circe.syntax.*
import java.nio.file.{Files, Paths}
import java.util.UUID
import services.cases.api.LoadFixtureMessage
import services.cases.objects.*
import services.games.api.*
import services.games.tables.SessionTable.SessionTableInitializer
import services.games.tables.CommitTable.CommitTableInitializer
import services.games.tables.ReceiptTable.ReceiptTableInitializer
import services.system.objects.*

// Local application entry point; it is not a route or an authentication service.
object FixtureDemo extends IOApp.Simple:
  override def run: IO[Unit] =
    // Resolve once; never silently open a new database during restoration.
    // Windows and WSL keep separate runtime files as well as separate compiler caches.
    val platform = if System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows") then "windows" else "linux"
    val database = Paths.get("target", "fixture-demo", platform, "db").toAbsolutePath.normalize()
    val url = s"jdbc:h2:file:${database};LOCK_TIMEOUT=10000"
    val restoreUrl = s"${url};IFEXISTS=TRUE"
    val user = UserId("local-demo")
    for
      _ <- IO.blocking(Files.createDirectories(database.getParent))
      _ <- IO.println(s"数据库文件：${database}.mv.db")
      id <- IO.delay(SessionId(UUID.randomUUID().toString))
      expected <- DatabaseSession.connection(url).use { connection =>
        for
          _ <- SessionTableInitializer.initialize(connection)
          _ <- CommitTableInitializer.initialize(connection)
          _ <- ReceiptTableInitializer.initialize(connection)
          fixture <- LoadFixtureMessage(Paths.get("../docs/fixtures/minimal_investigation.json")).plan(connection)
          started <- DatabaseSession.transaction(connection)(StartGameMessage(user, id, fixture).plan(connection))
          hidden <- DatabaseSession.transaction(connection)(InvestigateMessage(user, id, RequestId("too-early"), Sequence(0), PointId("point_letter"), RouteId("route_letter")).plan(connection)).attempt
          _ <- IO.println(s"会话 ${started.session.value}；提前阅读：${hidden.left.map(_.getMessage)}")
          opened <- DatabaseSession.transaction(connection)(ExecuteOperationMessage(user, id, RequestId("open"), Sequence(0), OperationId("open_drawer")).plan(connection))
          learned <- DatabaseSession.transaction(connection)(InvestigateMessage(user, id, RequestId("read"), opened.result.sequence, PointId("point_letter"), RouteId("route_letter")).plan(connection))
          retry <- DatabaseSession.transaction(connection)(InvestigateMessage(user, id, RequestId("read"), opened.result.sequence, PointId("point_letter"), RouteId("route_letter")).plan(connection))
          _ <- IO.println(s"获得线索：${learned.asJson.noSpaces}；相同请求重试返回相同结果：${retry == learned}")
          _ <- IO.raiseUnless(retry == learned)(new IllegalStateException("相同请求返回了不同结果。"))
          beforeClose <- DatabaseSession.transaction(connection)(GetGameStateMessage(user, id).plan(connection))
        yield beforeClose
      }
      // Reopen after closing the connection: restore from stored initial state and journal.
      _ <- DatabaseSession.connection(restoreUrl).use { connection =>
        DatabaseSession.transaction(connection)(GetGameStateMessage(user, id).plan(connection))
          .flatMap { state =>
            IO.raiseUnless(state == expected && state.sequence == Sequence(2) && state.clues.contains(ClueId("clue_letter")) &&
              state.facts.contains(FactId("fact_letter_text")))(
                new IllegalStateException("恢复结果不符合最小调查预期。")) *>
              IO.println(s"关闭连接后恢复：${state.asJson.noSpaces}")
          }
      }.handleErrorWith { error =>
        IO.println(s"[验收失败] 恢复失败；待恢复会话：${id.value}；数据库：${database}.mv.db") *> IO.raiseError(error)
      }
      _ <- IO.println("[验收通过] 调查、请求去重、关闭数据库后恢复均成功。")
    yield ()
