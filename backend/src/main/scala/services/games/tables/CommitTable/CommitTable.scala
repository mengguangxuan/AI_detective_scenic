package services.games.tables.CommitTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.{Connection, ResultSet}
import scala.util.Using
import services.system.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*

object CommitTable:
  private val insertSql = "INSERT INTO game_commits(session_id, sequence_no, body) VALUES (?, ?, ?)"
  def insert(connection: Connection, commit: FixtureCommit): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(insertSql)) { stmt =>
      stmt.setString(1, commit.session.value)
      stmt.setLong(2, commit.sequence.value)
      stmt.setString(3, commit.asJson.noSpaces)
      stmt.executeUpdate()
    }
    ()
  }

  private val listSql = "SELECT body FROM game_commits WHERE session_id = ? ORDER BY sequence_no"
  def list(connection: Connection, session: SessionId): IO[Vector[FixtureCommit]] = IO.blocking {
    Using.resource(connection.prepareStatement(listSql)) { stmt =>
      stmt.setString(1, session.value)
      Using.resource(stmt.executeQuery()) { rows =>
        val result = Vector.newBuilder[FixtureCommit]
        while rows.next() do result += decode[FixtureCommit](rows.getString("body")).fold(throw _, identity)
        result.result()
      }
    }
  }
