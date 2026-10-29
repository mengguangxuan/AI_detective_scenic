package services.games.tables.PoliceExchangeTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.Connection
import scala.util.Using
import services.games.objects.PoliceExchangeRecord
import services.system.objects.SessionId

object PoliceExchangeTable:
  private val insertSql = "INSERT INTO police_exchanges(session_id,request_id,sequence_no,payload_json) VALUES (?,?,?,?)"
  def insert(connection: Connection, row: PoliceExchangeRecord): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(insertSql)) { s =>
      s.setString(1,row.sessionId.value); s.setString(2,row.requestId.value)
      s.setLong(3,row.sequence.value); s.setString(4,row.asJson.noSpaces); s.executeUpdate()
    }
    ()
  }
  private val listSql = "SELECT payload_json FROM police_exchanges WHERE session_id=? ORDER BY sequence_no"
  def list(connection: Connection, session: SessionId): IO[Vector[PoliceExchangeRecord]] = IO.blocking {
    Using.resource(connection.prepareStatement(listSql)) { s =>
      s.setString(1,session.value)
      Using.resource(s.executeQuery()) { r =>
        val rows=Vector.newBuilder[PoliceExchangeRecord]
        while r.next() do rows += decode[PoliceExchangeRecord](r.getString(1)).fold(throw _,identity)
        rows.result()
      }
    }
  }
