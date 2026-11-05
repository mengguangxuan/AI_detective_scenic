package services.sceneproduction.tables.SceneProductionTable
import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.{Connection,ResultSet}
import scala.util.Using
import services.sceneproduction.objects.*
import services.system.objects.UserId
object SceneProductionTable:
  private val insertSql="INSERT INTO scene_productions(production_id,owner_id,input_json,phase,created_at,saved_at) VALUES(?,?,?,?,?,?)"
  def insert(c: Connection,row: SceneProductionRecord): IO[Unit] = IO.blocking {
    Using.resource(c.prepareStatement(insertSql)){s=>s.setString(1,row.id.value);s.setString(2,row.owner.value);s.setString(3,row.input.asJson.noSpaces);s.setString(4,row.phase.toString);s.setString(5,row.createdAt);s.setString(6,row.savedAt.orNull);s.executeUpdate()};()
  }
  private val findSql="SELECT * FROM scene_productions WHERE production_id=?"
  private val lockSql=findSql+" FOR UPDATE"
  private def one(c: Connection,id: SceneProductionId,sql: String): IO[Option[SceneProductionRecord]] = IO.blocking {
    Using.resource(c.prepareStatement(sql)){s=>s.setString(1,id.value);Using.resource(s.executeQuery())(r=>if r.next() then Some(read(r)) else None)}
  }
  def find(c: Connection,id: SceneProductionId): IO[Option[SceneProductionRecord]]=one(c,id,findSql)
  def lock(c: Connection,id: SceneProductionId): IO[Option[SceneProductionRecord]]=one(c,id,lockSql)
  private val listSql="SELECT * FROM scene_productions WHERE owner_id=? ORDER BY created_at DESC,production_id DESC"
  def list(c: Connection,user: UserId): IO[Vector[SceneProductionRecord]]=IO.blocking {
    Using.resource(c.prepareStatement(listSql)){s=>s.setString(1,user.value);Using.resource(s.executeQuery()){ r=>
      val b=Vector.newBuilder[SceneProductionRecord]
      while r.next() do b+=read(r)
      b.result()
    }}
  }
  private val inputSql="UPDATE scene_productions SET input_json=? WHERE production_id=? AND phase='Draft'"
  def updateInput(c: Connection,id: SceneProductionId,input: SceneAuthorInput): IO[Unit]=IO.blocking {
    Using.resource(c.prepareStatement(inputSql)){s=>s.setString(1,input.asJson.noSpaces);s.setString(2,id.value);require(s.executeUpdate()==1)}
  }
  private val queuedSql="UPDATE scene_productions SET phase='Queued' WHERE production_id=? AND phase='Draft'"
  def markQueued(c: Connection,id: SceneProductionId): IO[Unit]=IO.blocking {Using.resource(c.prepareStatement(queuedSql)){s=>s.setString(1,id.value);require(s.executeUpdate()==1)}}
  private val savedSql="UPDATE scene_productions SET phase='Saved',saved_at=? WHERE production_id=? AND phase='Queued'"
  def markSaved(c: Connection,id: SceneProductionId,at: String): IO[Unit]=IO.blocking {Using.resource(c.prepareStatement(savedSql)){s=>s.setString(1,at);s.setString(2,id.value);require(s.executeUpdate()==1)}}
  private def read(r: ResultSet): SceneProductionRecord = SceneProductionRecord(SceneProductionId(r.getString("production_id")),UserId(r.getString("owner_id")),decode[SceneAuthorInput](r.getString("input_json")).fold(throw _,identity),SceneProductionPhase.valueOf(r.getString("phase")),r.getString("created_at"),Option(r.getString("saved_at")))
