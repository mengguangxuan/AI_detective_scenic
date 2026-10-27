package services.games.utils

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*
import services.games.tables.SessionTable.SessionTable
import services.games.tables.SessionRecordTable.SessionRecordTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.system.objects.*

private[games] object MutationSupport:
  private[games] def owned(row: Option[GameSessionRecord],user: UserId): IO[GameSessionRecord] = row match
    case Some(s) if s.owner==user => IO.pure(s)
    case _ => IO.raiseError(GameError(ErrorCode.AccessDenied,"无法访问该会话。"))

  private[games] def operationDigest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,operation: OperationId): String =
    digest(user,session,request,expected,Json.obj("kind" -> Json.fromString("Execute"),"operation" -> operation.asJson))

  private[games] def investigationDigest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,point: PointId,route: RouteId): String =
    digest(user,session,request,expected,Json.obj("kind" -> Json.fromString("Investigate"),"point" -> point.asJson,"route" -> route.asJson))

  // Keep the exact original JSON bytes so stored receipt digests remain valid.
  private def digest(user: UserId,session: SessionId,request: RequestId,expected: Sequence,command: Json): String =
    val payload=Json.obj("user" -> user.asJson,"session" -> session.asJson,"request" -> request.asJson,
      "expected" -> expected.asJson,"command" -> command).noSpaces
    MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)).map(b => f"${b & 0xff}%02x").mkString

  private[games] def retry(receipt: RequestReceiptRecord,digest: String): IO[CommandResult] =
    if receipt.digest==digest then IO.pure(receipt.result)
    else IO.raiseError(GameError(ErrorCode.RequestConflict,"同一请求编号的内容发生变化。"))

  private[games] def checkSequence(row: GameSessionRecord,expected: Sequence): IO[Unit] =
    IO.raiseUnless(row.sequence==expected)(GameError(ErrorCode.StaleState,"会话已发生变化，请刷新。"))

  private[games] def commit(connection: Connection,previous: GameSessionRecord,request: RequestId,digest: String,plan: ActionPlan): IO[CommandResult] =
    for
      _ <- SessionTable.update(connection,previous,plan.session)
      _ <- SessionRecordTable.insert(connection,plan.record)
      _ <- ReceiptTable.insert(connection,RequestReceiptRecord(previous.id,request,digest,1,plan.result))
    yield plan.result
