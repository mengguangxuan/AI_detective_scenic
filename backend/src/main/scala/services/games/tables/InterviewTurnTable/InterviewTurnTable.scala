package services.games.tables.InterviewTurnTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.Connection
import scala.util.Using
import services.games.objects.InterviewTurnRecord
import services.system.objects.SessionId

object InterviewTurnTable:
  private val insertSql = "INSERT INTO interview_turns(session_id,request_id,sequence_no,witness_id,payload_json) VALUES (?,?,?,?,?)"
  def insert(connection: Connection, row: InterviewTurnRecord): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(insertSql)) { s =>
      s.setString(1,row.sessionId.value); s.setString(2,row.requestId.value); s.setLong(3,row.sequence.value)
      s.setString(4,row.witness.value); s.setString(5,row.asJson.noSpaces); s.executeUpdate()
    }
    ()
  }
  private val listSql = "SELECT payload_json FROM interview_turns WHERE session_id=? ORDER BY sequence_no"
  def list(connection: Connection, session: SessionId): IO[Vector[InterviewTurnRecord]] = IO.blocking {
    Using.resource(connection.prepareStatement(listSql)) { s =>
      s.setString(1,session.value)
      Using.resource(s.executeQuery()) { r =>
        val rows=Vector.newBuilder[InterviewTurnRecord]
        while r.next() do rows += decode[InterviewTurnRecord](r.getString(1)).fold(throw _,identity)
        rows.result()
      }
    }
  }
