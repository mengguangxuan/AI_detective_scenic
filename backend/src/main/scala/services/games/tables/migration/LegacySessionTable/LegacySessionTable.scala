package services.games.tables.migration.LegacySessionTable

import cats.effect.IO
import io.circe.Json
import io.circe.parser.{parse,decode}
import java.sql.Connection
import scala.util.Using
import services.games.objects.migration.*
import services.games.objects.apiTypes.CommandResult
import services.system.objects.*

object LegacySessionTable:
  private val listSql="SELECT body,sequence_no FROM game_sessions ORDER BY session_id"
  def list(connection: Connection): IO[Vector[LegacySessionSnapshot]] = IO.blocking {
    Using.resource(connection.createStatement()) { s =>
      Using.resource(s.executeQuery(listSql)) { rows =>
        val result=Vector.newBuilder[LegacySessionSnapshot]
        while rows.next() do
          val snapshot=decode[LegacySessionSnapshot](rows.getString("body")).fold(throw _,identity)
          result += snapshot.copy(sequence=Sequence(rows.getLong("sequence_no")))
        result.result()
      }
    }
  }
