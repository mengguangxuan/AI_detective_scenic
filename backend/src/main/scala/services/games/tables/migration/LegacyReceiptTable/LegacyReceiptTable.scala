package services.games.tables.migration.LegacyReceiptTable

import cats.effect.IO
import io.circe.Json
import io.circe.parser.{parse,decode}
import java.sql.Connection
import scala.util.Using
import services.games.objects.migration.*
import services.games.objects.apiTypes.CommandResult
import services.system.objects.*

object LegacyReceiptTable:
  private val listSql="SELECT request_id,digest,response FROM game_receipts WHERE session_id=? ORDER BY request_id"
  def list(connection: Connection,session: SessionId): IO[Vector[LegacyReceiptSnapshot]] = IO.blocking {
    Using.resource(connection.prepareStatement(listSql)) { s =>
      s.setString(1,session.value)
      Using.resource(s.executeQuery()) { rows =>
        val result=Vector.newBuilder[LegacyReceiptSnapshot]
        while rows.next() do result += LegacyReceiptSnapshot(session,RequestId(rows.getString("request_id")),rows.getString("digest"),decode[CommandResult](rows.getString("response")).fold(throw _,identity))
        result.result()
      }
    }
  }
