package services.cases.tables.CaseRevisionTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.{Connection, ResultSet}
import scala.util.Using
import services.system.objects.*
import services.cases.objects.*
import services.cases.utils.FixtureLoader

object CaseRevisionTable:
  private val insertSql = "INSERT INTO case_revisions(case_id,revision,content_schema_version,title,body_json) VALUES (?,?,?,?,?)"
  def insert(connection: Connection, record: CaseRevisionRecord): IO[Unit] = IO.blocking {
    val checked = FixtureLoader.validation(record)
    require(checked.isRight, checked.left.getOrElse(""))
    Using.resource(connection.prepareStatement(insertSql)) { s =>
      s.setString(1,record.caseId.value); s.setInt(2,record.revision.value)
      s.setInt(3,record.contentSchemaVersion); s.setString(4,record.title)
      s.setString(5,record.content.asJson.noSpaces); s.executeUpdate()
    }
    ()
  }

  private val findSql = "SELECT * FROM case_revisions WHERE case_id=? AND revision=?"
  def find(connection: Connection, id: CaseId, revision: Revision): IO[Option[CaseRevisionRecord]] = IO.blocking {
    Using.resource(connection.prepareStatement(findSql)) { s =>
      s.setString(1,id.value); s.setInt(2,revision.value)
      Using.resource(s.executeQuery())(rows => if rows.next() then Some(read(rows)) else None)
    }
  }

  private def read(rows: ResultSet): CaseRevisionRecord =
    if rows.getInt("content_schema_version") != 1 then throw GameError(ErrorCode.InvalidValue,"不支持的案件存储版本。")
    val content = decode[CaseContent](rows.getString("body_json")).fold(throw _, identity)
    val record = CaseRevisionRecord(CaseId(rows.getString("case_id")),Revision(rows.getInt("revision")),1,rows.getString("title"),content)
    FixtureLoader.validation(record).fold(
      message => throw GameError(ErrorCode.InvalidValue,s"已存案件引用不合法：$message"), identity)
    record

  // A stable existing row serializes retryable creation of otherwise absent session IDs.
  private val lockSql = "SELECT * FROM case_revisions WHERE case_id=? AND revision=? FOR UPDATE"
  def lock(connection: Connection, id: CaseId, revision: Revision): IO[Option[CaseRevisionRecord]] = IO.blocking {
    Using.resource(connection.prepareStatement(lockSql)) { statement =>
      statement.setString(1,id.value)
      statement.setInt(2,revision.value)
      Using.resource(statement.executeQuery())(rows => if rows.next() then Some(read(rows)) else None)
    }
  }
