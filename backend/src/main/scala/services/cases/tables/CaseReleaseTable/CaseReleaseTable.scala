package services.cases.tables.CaseReleaseTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.{Connection, ResultSet}
import scala.util.Using
import services.cases.objects.*
import services.system.objects.Revision

object CaseReleaseTable:
  private val insertSql = "INSERT INTO case_releases(case_id,revision,summary,tags_json,scene_bundle,scene_revision) VALUES (?,?,?,?,?,?)"
  def insert(connection: Connection, row: CaseReleaseRecord): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(insertSql)) { s =>
      s.setString(1,row.caseId.value); s.setInt(2,row.revision.value); s.setString(3,row.summary)
      s.setString(4,row.tags.asJson.noSpaces); s.setString(5,row.sceneBundle); s.setString(6,row.sceneRevision)
      s.executeUpdate()
    }
    ()
  }

  private val findSql = "SELECT * FROM case_releases WHERE case_id=? AND revision=?"
  def find(connection: Connection,id: CaseId,revision: Revision): IO[Option[CaseReleaseRecord]] = IO.blocking {
    Using.resource(connection.prepareStatement(findSql)) { s =>
      s.setString(1,id.value); s.setInt(2,revision.value)
      Using.resource(s.executeQuery())(r => if r.next() then Some(read(r)) else None)
    }
  }

  private val catalogSql = """SELECT p.*,c.title FROM case_releases p JOIN case_revisions c ON c.case_id=p.case_id AND c.revision=p.revision
    WHERE p.revision=(SELECT MAX(latest.revision) FROM case_releases latest WHERE latest.case_id=p.case_id) ORDER BY p.case_id"""
  def catalog(connection: Connection): IO[Vector[CaseCatalogEntry]] = IO.blocking {
    Using.resource(connection.prepareStatement(catalogSql)) { s => Using.resource(s.executeQuery()) { rows =>
      val result=Vector.newBuilder[CaseCatalogEntry]
      while rows.next() do
        val release=read(rows)
        result += CaseCatalogEntry(release.caseId,release.revision,rows.getString("title"),release.summary,release.tags)
      result.result()
    } }
  }

  private def read(r: ResultSet): CaseReleaseRecord =
    CaseReleaseRecord(CaseId(r.getString("case_id")),Revision(r.getInt("revision")),r.getString("summary"),
      decode[Vector[String]](r.getString("tags_json")).fold(throw _,identity),r.getString("scene_bundle"),r.getString("scene_revision"))
