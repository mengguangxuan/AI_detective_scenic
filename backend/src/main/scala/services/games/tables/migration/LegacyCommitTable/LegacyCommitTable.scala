package services.games.tables.migration.LegacyCommitTable

import cats.effect.IO
import io.circe.Json
import io.circe.parser.{parse,decode}
import java.sql.Connection
import scala.util.Using
import services.games.objects.migration.*
import services.games.objects.apiTypes.CommandResult
import services.system.objects.*

object LegacyCommitTable:
  private val listSql="SELECT body FROM game_commits WHERE session_id=? ORDER BY sequence_no"
  def list(connection: Connection,session: SessionId): IO[Vector[LegacyCommitSnapshot]] = IO.blocking {
    Using.resource(connection.prepareStatement(listSql)) { s =>
      s.setString(1,session.value)
      Using.resource(s.executeQuery()) { rows =>
        val result=Vector.newBuilder[LegacyCommitSnapshot]
        while rows.next() do
          val c=parse(rows.getString("body")).fold(throw _,identity).hcursor
          def field[A: io.circe.Decoder](name: String): A=c.get[A](name).fold(throw _,identity)
          val version=c.downField("reducerVersion").get[String]("value").fold(throw _,identity)
          result += LegacyCommitSnapshot(field[SessionId]("session"),field[Sequence]("sequence"),field[RequestId]("request"),version,field[Vector[Json]]("changes"))
        result.result()
      }
    }
  }
