package services.games.tables.SessionTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.{Connection, ResultSet}
import scala.util.Using
import services.system.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*

object SessionTable:
  private val insertSql = "INSERT INTO game_sessions(session_id, owner_id, body, sequence_no) VALUES (?, ?, ?, 0)"
  def insert(connection: Connection, row: StoredSession): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(insertSql)) { stmt =>
      stmt.setString(1, row.id.value)
      stmt.setString(2, row.owner.value)
      stmt.setString(3, row.asJson.noSpaces)
      stmt.executeUpdate()
    }
    ()
  }

  private val findSql = "SELECT body, sequence_no FROM game_sessions WHERE session_id = ?"
  def find(connection: Connection, id: SessionId): IO[Option[StoredSession]] =
    IO.blocking(query(connection, findSql, id))

  private val lockSql = "SELECT body, sequence_no FROM game_sessions WHERE session_id = ? FOR UPDATE"
  def lock(connection: Connection, id: SessionId): IO[Option[StoredSession]] =
    IO.blocking(query(connection, lockSql, id))

  private def query(connection: Connection, sql: String, id: SessionId): Option[StoredSession] =
    Using.resource(connection.prepareStatement(sql)) { stmt =>
      stmt.setString(1, id.value)
      Using.resource(stmt.executeQuery()) { rows =>
        if rows.next() then
          val stored = decode[StoredSession](rows.getString("body")).fold(throw _, identity)
          Some(stored.copy(sequence = Sequence(rows.getLong("sequence_no"))))
        else None
      }
    }

  private val advanceSql = "UPDATE game_sessions SET sequence_no = ? WHERE session_id = ? AND sequence_no = ?"
  def advance(connection: Connection, id: SessionId, expected: Sequence, next: Sequence): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(advanceSql)) { stmt =>
      stmt.setLong(1, next.value)
      stmt.setString(2, id.value)
      stmt.setLong(3, expected.value)
      if stmt.executeUpdate() != 1 then throw GameError(ErrorCode.StaleState, "会话已发生变化。")
    }
  }
