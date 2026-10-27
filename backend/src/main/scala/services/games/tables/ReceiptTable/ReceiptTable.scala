package services.games.tables.ReceiptTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.{Connection, PreparedStatement, ResultSet}
import scala.util.Using
import services.system.objects.*
import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*

object ReceiptTable:
  private val findSql = "SELECT digest,response_schema_version,response_json FROM game_receipts WHERE session_id=? AND request_id=?"
  def find(connection: Connection, session: SessionId, request: RequestId): IO[Option[RequestReceiptRecord]] = IO.blocking {
    Using.resource(connection.prepareStatement(findSql)) { s =>
      s.setString(1,session.value); s.setString(2,request.value)
      Using.resource(s.executeQuery()) { r =>
        if r.next() then
          if r.getInt("response_schema_version")!=1 then throw GameError(ErrorCode.InvalidValue,"不支持的回执版本。")
          Some(RequestReceiptRecord(session,request,r.getString("digest"),1,decode[CommandResult](r.getString("response_json")).fold(throw _,identity)))
        else None
      }
    }
  }

  private val insertSql = "INSERT INTO game_receipts(session_id,request_id,digest,response_schema_version,response_json) VALUES (?,?,?,?,?)"
  def insert(connection: Connection, r: RequestReceiptRecord): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(insertSql)) { s =>
      s.setString(1,r.sessionId.value); s.setString(2,r.requestId.value); s.setString(3,r.digest)
      s.setInt(4,r.responseSchemaVersion); s.setString(5,r.result.asJson.noSpaces); s.executeUpdate()
    }
    ()
  }
