package services.games.tables.ReasoningRequestTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.Connection
import scala.util.Using
import services.games.objects.*
import services.system.objects.*

object ReasoningRequestTable:
  private val findSql = "SELECT payload_json FROM reasoning_requests WHERE session_id=? AND request_id=?"
  def find(connection: Connection, session: SessionId, request: RequestId): IO[Option[ReasoningRequestRecord]] = IO.blocking {
    Using.resource(connection.prepareStatement(findSql)) { s =>
      s.setString(1,session.value); s.setString(2,request.value)
      Using.resource(s.executeQuery())(r => if r.next() then Some(decode[ReasoningRequestRecord](r.getString(1)).fold(throw _,identity)) else None)
    }
  }
  private val activeSql = "SELECT 1 FROM reasoning_requests WHERE session_id=? AND status='Pending' AND lease_until>?"
  def hasActive(connection: Connection, session: SessionId, now: Long): IO[Boolean] = IO.blocking {
    Using.resource(connection.prepareStatement(activeSql)) { s =>
      s.setString(1,session.value); s.setLong(2,now)
      Using.resource(s.executeQuery())(_.next())
    }
  }
  private val insertSql = "INSERT INTO reasoning_requests(session_id,request_id,digest,lease_token,lease_until,status,payload_json) VALUES (?,?,?,?,?,?,?)"
  def insert(connection: Connection, row: ReasoningRequestRecord): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(insertSql)) { s =>
      s.setString(1,row.sessionId.value); s.setString(2,row.requestId.value); s.setString(3,row.digest)
      s.setString(4,row.leaseToken); s.setLong(5,row.leaseUntilMillis); s.setString(6,row.status.toString)
      s.setString(7,row.asJson.noSpaces); s.executeUpdate()
    }
    ()
  }
  private val replaceSql = "UPDATE reasoning_requests SET lease_token=?,lease_until=?,status=?,payload_json=? WHERE session_id=? AND request_id=? AND lease_token=?"
  def replace(connection: Connection, row: ReasoningRequestRecord, previousToken: String): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(replaceSql)) { s =>
      s.setString(1,row.leaseToken); s.setLong(2,row.leaseUntilMillis); s.setString(3,row.status.toString); s.setString(4,row.asJson.noSpaces)
      s.setString(5,row.sessionId.value); s.setString(6,row.requestId.value); s.setString(7,previousToken)
      if s.executeUpdate() != 1 then throw GameError(ErrorCode.RequestPending,"这次推理评审仍在处理中，请稍后用原请求重试。")
    }
    ()
  }
