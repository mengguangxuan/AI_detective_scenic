package services.system.api

import cats.effect.{IO, IOApp}
import io.circe.syntax.*
import java.nio.file.Paths
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
    val url = "jdbc:h2:file:./target/fixture-demo/db;LOCK_TIMEOUT=10000"
    val user = UserId("local-demo")
    for
      id <- IO.delay(SessionId(UUID.randomUUID().toString))
      _ <- DatabaseSession.connection(url).use { connection =>
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
        yield ()
      }
      // Reopen after closing the connection: restore from stored initial state and journal.
      _ <- DatabaseSession.connection(url).use { connection =>
        DatabaseSession.transaction(connection)(GetGameStateMessage(user, id).plan(connection))
          .flatMap(state => IO.println(s"关闭连接后恢复：${state.asJson.noSpaces}"))
      }
    yield ()
