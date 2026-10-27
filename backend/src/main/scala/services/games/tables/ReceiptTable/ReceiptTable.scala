package services.games.tables.ReceiptTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.{Connection, ResultSet}
import scala.util.Using
import services.system.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*

object ReceiptTable:
  private val findSql = "SELECT digest, response FROM game_receipts WHERE session_id = ? AND request_id = ?"
  def find(connection: Connection, session: SessionId, request: RequestId): IO[Option[StoredReceipt]] = IO.blocking {
    Using.resource(connection.prepareStatement(findSql)) { stmt =>
      stmt.setString(1, session.value)
      stmt.setString(2, request.value)
      Using.resource(stmt.executeQuery()) { rows =>
        if rows.next() then Some(StoredReceipt(rows.getString("digest"),
          decode[CommandResult](rows.getString("response")).fold(throw _, identity)))
        else None
      }
    }
  }

  private val insertSql = "INSERT INTO game_receipts(session_id, request_id, digest, response) VALUES (?, ?, ?, ?)"
  def insert(connection: Connection, session: SessionId, request: RequestId, receipt: StoredReceipt): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(insertSql)) { stmt =>
      stmt.setString(1, session.value)
      stmt.setString(2, request.value)
      stmt.setString(3, receipt.digest)
      stmt.setString(4, receipt.response.asJson.noSpaces)
      stmt.executeUpdate()
    }
    ()
  }
