package services.cases.tables.CaseReleaseTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.Connection
import scala.util.Using
import services.cases.objects.*

object CaseReleaseTable:
  private val insertSql = "INSERT INTO case_releases(case_id,summary,tags_json,scene_bundle) VALUES (?,?,?,?)"
  def insert(connection: Connection, row: CaseReleaseRecord): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(insertSql)) { s =>
      s.setString(1,row.caseId.value); s.setString(2,row.summary)
      s.setString(3,row.tags.asJson.noSpaces); s.setString(4,row.sceneBundle); s.executeUpdate()
    }
    ()
  }

  private val findSql = "SELECT * FROM case_releases WHERE case_id=?"
  def find(connection: Connection,id: CaseId): IO[Option[CaseReleaseRecord]] = IO.blocking {
    Using.resource(connection.prepareStatement(findSql)) { s =>
      s.setString(1,id.value)
      Using.resource(s.executeQuery())(rows => if rows.next() then Some(read(rows)) else None)
    }
  }

  private val catalogSql = "SELECT p.*,c.title FROM case_releases p JOIN cases c ON c.case_id=p.case_id ORDER BY p.case_id"
  def catalog(connection: Connection): IO[Vector[CaseCatalogEntry]] = IO.blocking {
    Using.resource(connection.prepareStatement(catalogSql)) { s => Using.resource(s.executeQuery()) { rows =>
      val result=Vector.newBuilder[CaseCatalogEntry]
      while rows.next() do
        val release=read(rows)
        result += CaseCatalogEntry(release.caseId,rows.getString("title"),release.summary,release.tags)
      result.result()
    } }
  }

  private def read(rows: java.sql.ResultSet): CaseReleaseRecord =
    CaseReleaseRecord(CaseId(rows.getString("case_id")),rows.getString("summary"),
      decode[Vector[String]](rows.getString("tags_json")).fold(throw _,identity),rows.getString("scene_bundle"))
