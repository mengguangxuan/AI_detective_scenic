package services.cases.tables.CaseTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.Connection
import scala.util.Using
import services.cases.objects.*
import services.cases.utils.FixtureLoader
import services.system.objects.*

object CaseTable:
  private val insertSql = "INSERT INTO cases(case_id,content_schema_version,title,body_json) VALUES (?,?,?,?)"
  def insert(connection: Connection, record: CaseRecord): IO[Unit] = IO.blocking {
    FixtureLoader.validation(record).fold(message => throw GameError(ErrorCode.InvalidValue,message), identity)
    Using.resource(connection.prepareStatement(insertSql)) { s =>
      s.setString(1,record.caseId.value); s.setInt(2,record.contentSchemaVersion)
      s.setString(3,record.title); s.setString(4,record.content.asJson.noSpaces); s.executeUpdate()
    }
    ()
  }

  private val findSql = "SELECT * FROM cases WHERE case_id=?"
  def find(connection: Connection, id: CaseId): IO[Option[CaseRecord]] = IO.blocking {
    Using.resource(connection.prepareStatement(findSql)) { s =>
      s.setString(1,id.value)
      Using.resource(s.executeQuery())(rows => if rows.next() then Some(read(rows)) else None)
    }
  }

  private val lockSql = "SELECT * FROM cases WHERE case_id=? FOR UPDATE"
  def lock(connection: Connection, id: CaseId): IO[Option[CaseRecord]] = IO.blocking {
    Using.resource(connection.prepareStatement(lockSql)) { s =>
      s.setString(1,id.value)
      Using.resource(s.executeQuery())(rows => if rows.next() then Some(read(rows)) else None)
    }
  }

  private def read(rows: java.sql.ResultSet): CaseRecord =
    if rows.getInt("content_schema_version") != 1 then throw GameError(ErrorCode.InvalidValue,"不支持的案件数据格式。")
    val content=decode[CaseContent](rows.getString("body_json")).fold(throw _,identity)
    val record=CaseRecord(CaseId(rows.getString("case_id")),1,rows.getString("title"),content)
    FixtureLoader.validation(record).fold(message => throw GameError(ErrorCode.InvalidValue,s"已存案件不合法：$message"),identity)
    record
