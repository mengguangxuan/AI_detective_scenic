package services.games.utils

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import services.games.objects.*
import services.games.objects.apiTypes.*
import services.games.tables.SessionTable.SessionTable
import services.games.tables.CommitTable.CommitTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.system.objects.*

private[games] object MutationSupport:
  private[games] def lockOwned(connection: Connection, user: UserId, session: SessionId): IO[StoredSession] =
    SessionTable.lock(connection, session).flatMap {
      case Some(row) if row.owner == user => IO.pure(row)
      case _ => IO.raiseError(GameError(ErrorCode.AccessDenied, "无法访问该会话。"))
    }

  private[games] def digest(user: UserId, session: SessionId, request: RequestId,
      expected: Sequence, command: FixtureCommand): String =
    val payload = Json.obj("user" -> user.asJson, "session" -> session.asJson,
      "request" -> request.asJson, "expected" -> expected.asJson, "command" -> command.asJson).noSpaces
    MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)).map(b => f"${b & 0xff}%02x").mkString

  private[games] def retry(receipt: StoredReceipt, digest: String): IO[CommandResult] =
    if receipt.digest == digest then IO.pure(receipt.response)
    else IO.raiseError(GameError(ErrorCode.RequestConflict, "同一请求编号的内容发生变化。"))

  private[games] def stateForNewRequest(connection: Connection, row: StoredSession, expected: Sequence): IO[FixtureState] =
    if expected != row.sequence then IO.raiseError(GameError(ErrorCode.StaleState, "会话已发生变化，请刷新。"))
    else CommitTable.list(connection, row.id).flatMap(log => IO.fromEither(FixtureRules.replay(row, log)))

  private[games] def commit(connection: Connection, row: StoredSession, request: RequestId,
      digest: String, changes: Vector[FixtureChange]): IO[CommandResult] =
    val sequence = Sequence(row.sequence.value + 1)
    val result = FixtureRules.result(sequence, changes)
    for
      _ <- IO.raiseWhen(changes.isEmpty)(GameError(ErrorCode.InvalidValue, "不能提交空日志。"))
      _ <- CommitTable.insert(connection, FixtureCommit(row.id, sequence, request, row.fixture.reducerVersion, changes))
      _ <- SessionTable.advance(connection, row.id, row.sequence, sequence)
      _ <- ReceiptTable.insert(connection, row.id, request, StoredReceipt(digest, result))
    yield result
