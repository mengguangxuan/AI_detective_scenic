package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import io.circe.parser.decode
import io.circe.syntax.*
import java.nio.file.{Files, Paths}
import java.sql.{Connection, DriverManager}
import java.util.UUID
import munit.FunSuite
import services.cases.api.LoadFixtureMessage
import services.cases.objects.*
import services.games.api.*
import services.games.objects.*
import services.games.tables.SessionTable.{SessionTable, SessionTableInitializer}
import services.games.tables.CommitTable.{CommitTable, CommitTableInitializer}
import services.games.tables.ReceiptTable.{ReceiptTable, ReceiptTableInitializer}
import services.games.utils.FixtureRules
import services.system.api.DatabaseSession
import services.system.objects.*

class MinimalFixtureSuite extends FunSuite:
  private val user = UserId("detective")
  private val session = SessionId("session")
  private val fixturePath = Paths.get("../docs/fixtures/minimal_investigation.json")

  private def setup(connection: Connection): IO[InvestigationFixture] = for
    _ <- SessionTableInitializer.initialize(connection)
    _ <- CommitTableInitializer.initialize(connection)
    _ <- ReceiptTableInitializer.initialize(connection)
    fixture <- LoadFixtureMessage(fixturePath).plan(connection)
    _ <- DatabaseSession.transaction(connection)(StartGameMessage(user, session, fixture).plan(connection))
  yield fixture

  private def withGame[A](f: (String, Connection, InvestigationFixture) => IO[A]): A =
    val url = s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
    DatabaseSession.connection(url).use(c => setup(c).flatMap(fixture => f(url, c, fixture)))
      .guarantee(DatabaseSession.connection(url).use(c => IO.blocking(c.createStatement().execute("SHUTDOWN")).void)).unsafeRunSync()

  private def open(c: Connection, id: String = "open", seq: Long = 0) =
    DatabaseSession.transaction(c)(ExecuteOperationMessage(user, session, RequestId(id), Sequence(seq), OperationId("open_drawer")).plan(c))
  private def read(c: Connection, id: String = "read", seq: Long = 1) =
    DatabaseSession.transaction(c)(InvestigateMessage(user, session, RequestId(id), Sequence(seq), PointId("point_letter"), RouteId("route_letter")).plan(c))
  private def view(c: Connection) = DatabaseSession.transaction(c)(GetGameStateMessage(user, session).plan(c))
  private def errorCode(result: Either[Throwable, ?]): ErrorCode = result match
    case Left(error: GameError) => error.code
    case other => fail(s"Expected GameError, got $other")

  test("hidden ancestor blocks direct point access; failed request writes nothing") {
    withGame { (_, c, _) => for
      initial <- view(c)
      result <- read(c, seq = 0).attempt
      log <- CommitTable.list(c, session)
      receipt <- ReceiptTable.find(c, session, RequestId("read"))
      _ = assertEquals(initial.visiblePoints, Vector(PointId("point_drawer")))
      _ = assertEquals(errorCode(result), ErrorCode.AccessDenied)
      _ = assert(log.isEmpty && receipt.isEmpty)
    yield () }
  }

  test("open -> investigate -> Acquired -> close/reopen yields same knowledge") {
    withGame { (url, c, _) => for
      opened <- open(c)
      visible <- view(c)
      learned <- read(c)
      original <- view(c)
      restored <- DatabaseSession.connection(url).use(view)
      log <- CommitTable.list(c, session)
      _ = assertEquals(opened.result.sequence, Sequence(1))
      _ = assert(visible.visiblePoints.contains(PointId("point_letter")))
      _ = assertEquals(learned.result.findings.map(_.value), Vector("信上写着：今晚九点见。"))
      _ = assertEquals(original.clues, Set(ClueId("clue_letter")))
      _ = assertEquals(original.facts, Set(FactId("fact_letter_text")))
      _ = assertEquals(restored, original)
      _ = assert(log.last.changes.last.isInstanceOf[FixtureChange.Acquired])
    yield () }
  }

  test("same request returns original response even after its expected sequence becomes stale") {
    withGame { (_, c, _) => for
      first <- open(c)
      retry <- open(c)
      _ <- read(c)
      retryLater <- open(c)
      log <- CommitTable.list(c, session)
      _ = assertEquals(retry, first)
      _ = assertEquals(retryLater, first)
      _ = assertEquals(log.size, 2)
    yield () }
  }

  test("same request ID with a different payload is rejected; stale new request cannot write") {
    withGame { (_, c, _) => for
      _ <- open(c)
      conflict <- read(c, id = "open", seq = 0).attempt
      stale <- read(c, id = "stale", seq = 0).attempt
      state <- view(c)
      _ = assertEquals(errorCode(conflict), ErrorCode.RequestConflict)
      _ = assertEquals(errorCode(stale), ErrorCode.StaleState)
      _ = assertEquals(state.sequence, Sequence(1))
      _ = assert(state.clues.isEmpty)
    yield () }
  }

  test("session ownership is checked before receipt lookup and replay") {
    withGame { (_, c, _) => for
      _ <- open(c)
      result <- DatabaseSession.transaction(c)(ExecuteOperationMessage(UserId("other"), session, RequestId("open"), Sequence(0), OperationId("open_drawer")).plan(c)).attempt
      _ = assertEquals(errorCode(result), ErrorCode.AccessDenied)
    yield () }
  }

  test("later database failure rolls back the journal, sequence and receipt together") {
    withGame { (_, c, _) => for
      result <- DatabaseSession.transaction(c)(
        ExecuteOperationMessage(user, session, RequestId("rollback"), Sequence(0), OperationId("open_drawer")).plan(c) *>
          IO.raiseError[Unit](new IllegalStateException("injected failure after all writes"))
      ).attempt
      after <- view(c)
      receipt <- ReceiptTable.find(c, session, RequestId("rollback"))
      commits <- CommitTable.list(c, session)
      _ = assert(result.isLeft)
      _ = assertEquals(after.sequence, Sequence(0))
      _ = assertEquals(after.visiblePoints, Vector(PointId("point_drawer")))
      _ = assert(receipt.isEmpty && commits.isEmpty)
    yield () }
  }

  test("two connections racing with the same sequence have exactly one winner") {
    withGame { (url, c, _) => for
      outcomes <- List("race-a", "race-b").parTraverse { id =>
        DatabaseSession.connection(url).use(other => open(other, id).attempt)
      }
      state <- view(c)
      log <- CommitTable.list(c, session)
      _ = assertEquals(outcomes.count(_.isRight), 1)
      _ = assertEquals(errorCode(outcomes.find(_.isLeft).get), ErrorCode.StaleState)
      _ = assertEquals(state.sequence, Sequence(1))
      _ = assertEquals(log.size, 1)
    yield () }
  }

  test("two connections retrying the same request return the same success and one commit") {
    withGame { (url, c, _) => for
      outcomes <- List(1, 2).parTraverse(_ => DatabaseSession.connection(url).use(other => open(other).attempt))
      log <- CommitTable.list(c, session)
      _ = assert(outcomes.forall(_.isRight))
      _ = assertEquals(outcomes.head, outcomes.last)
      _ = assertEquals(log.size, 1)
    yield () }
  }

  test("replay checks reducer version and contiguous journal order; inspection alone grants nothing") {
    withGame { (_, c, fixture) => for
      _ <- open(c)
      _ <- read(c)
      stored <- SessionTable.find(c, session).map(_.get)
      log <- CommitTable.list(c, session)
      _ = assert(FixtureRules.replay(stored, log.reverse).isLeft)
      wrong = log.updated(0, log.head.copy(reducerVersion = ReducerVersion("wrong")))
      _ = assert(FixtureRules.replay(stored, wrong).isLeft)
      onlyAttempt = log.last.copy(sequence = Sequence(1), changes = log.last.changes.take(1))
      replayed = FixtureRules.replay(stored.copy(sequence = Sequence(1)), Vector(onlyAttempt)).toOption.get
      _ = assert(replayed.facts.isEmpty && replayed.clues.isEmpty)
      _ = assertEquals(decode[FixtureCommit](log.last.asJson.noSpaces), Right(log.last))
    yield () }
  }

  test("file-backed database survives every connection closing") {
    val dir = Files.createTempDirectory("detective-h2-")
    val url = s"jdbc:h2:file:${dir.resolve("case").toAbsolutePath};LOCK_TIMEOUT=10000"
    (for
      before <- DatabaseSession.connection(url).use(c => setup(c) *> open(c) *> read(c) *> view(c))
      after <- DatabaseSession.connection(url).use(view)
      _ = assertEquals(after, before)
    yield ()).unsafeRunSync()
  }

  test("repeated sessions survive reopening an existing database without losing older sessions") {
    val dir = Files.createTempDirectory("detective-h2-repeat-")
    val database = dir.resolve("case").toAbsolutePath.normalize()
    val url = s"jdbc:h2:file:${database};LOCK_TIMEOUT=10000"
    val restoreUrl = s"${url};IFEXISTS=TRUE"
    (for
      _ <- DatabaseSession.connection(url).use { c =>
        SessionTableInitializer.initialize(c) *> CommitTableInitializer.initialize(c) *> ReceiptTableInitializer.initialize(c)
      }
      _ <- (1 to 20).toVector.traverse_ { n =>
        val id = SessionId(s"repeat-$n")
        for
          _ <- DatabaseSession.connection(restoreUrl).use { c => for
            fixture <- LoadFixtureMessage(fixturePath).plan(c)
            _ <- DatabaseSession.transaction(c)(StartGameMessage(user, id, fixture).plan(c))
            _ <- DatabaseSession.transaction(c)(ExecuteOperationMessage(user, id, RequestId("open"), Sequence(0), OperationId("open_drawer")).plan(c))
            _ <- DatabaseSession.transaction(c)(InvestigateMessage(user, id, RequestId("read"), Sequence(1), PointId("point_letter"), RouteId("route_letter")).plan(c))
          yield () }
          _ <- DatabaseSession.connection(restoreUrl).use { c =>
            (1 to n).toVector.traverse_ { previous =>
              DatabaseSession.transaction(c)(GetGameStateMessage(user, SessionId(s"repeat-$previous")).plan(c)).map { state =>
                assertEquals(state.sequence, Sequence(2))
                assertEquals(state.clues, Set(ClueId("clue_letter")))
                assertEquals(state.facts, Set(FactId("fact_letter_text")))
              }
            }
          }
        yield ()
      }
    yield ()).unsafeRunSync()
  }

  test("loader rejects unsupported execution branches instead of silently ignoring them") {
    val raw = Files.readString(fixturePath)
    val invalid = raw.replace("\"OperationDone\"", "\"TimeReached\"")
    val path = Files.createTempFile("detective-invalid-", ".json")
    Files.writeString(path, invalid)
    val outcome = DatabaseSession.connection("jdbc:h2:mem:invalid").use(c => LoadFixtureMessage(path).plan(c).attempt).unsafeRunSync()
    assertEquals(errorCode(outcome), ErrorCode.InvalidValue)
  }
