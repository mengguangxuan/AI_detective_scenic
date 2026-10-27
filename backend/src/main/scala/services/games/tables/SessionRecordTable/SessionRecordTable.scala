package services.games.tables.SessionRecordTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.{Connection, PreparedStatement, ResultSet}
import scala.util.Using
import services.system.objects.*
import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*

object SessionRecordTable:
  private val insertSql = "INSERT INTO session_records(record_id,session_id,request_id,sequence_no,story_time_seconds,point_id,route_id,operation_id,text,clues_json,facts_json) VALUES (?,?,?,?,?,?,?,?,?,?,?)"
  def insert(connection: Connection, r: SessionRecord): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(insertSql)) { s =>
      s.setString(1,r.id.value); s.setString(2,r.sessionId.value); s.setString(3,r.requestId.value)
      s.setLong(4,r.sequence.value); s.setLong(5,r.storyTimeSeconds); s.setString(6,r.point.value)
      s.setString(7,r.route.map(_.value).orNull); s.setString(8,r.operation.map(_.value).orNull)
      s.setString(9,r.text); s.setString(10,r.disclosedClues.asJson.noSpaces); s.setString(11,r.disclosedFacts.asJson.noSpaces)
      s.executeUpdate()
    }
    ()
  }

  private val listSql = "SELECT * FROM session_records WHERE session_id=? ORDER BY sequence_no"
  def list(connection: Connection, session: SessionId): IO[Vector[SessionRecord]] = IO.blocking {
    Using.resource(connection.prepareStatement(listSql)) { s =>
      s.setString(1,session.value)
      Using.resource(s.executeQuery()) { rows =>
        val result=Vector.newBuilder[SessionRecord]
        while rows.next() do result += read(rows)
        result.result()
      }
    }
  }

  private def read(r: ResultSet): SessionRecord =
    SessionRecord(RecordId(r.getString("record_id")),SessionId(r.getString("session_id")),RequestId(r.getString("request_id")),Sequence(r.getLong("sequence_no")),r.getLong("story_time_seconds"),PointId(r.getString("point_id")),Option(r.getString("route_id")).map(RouteId.apply),Option(r.getString("operation_id")).map(OperationId.apply),r.getString("text"),decode[Set[ClueId]](r.getString("clues_json")).fold(throw _,identity),decode[Set[FactId]](r.getString("facts_json")).fold(throw _,identity))
