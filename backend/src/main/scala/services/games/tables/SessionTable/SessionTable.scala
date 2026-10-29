package services.games.tables.SessionTable

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.{Connection, PreparedStatement, ResultSet}
import scala.util.Using
import services.system.objects.*
import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*

object SessionTable:
  private val insertSql = "INSERT INTO game_sessions(session_id,owner_id,case_id,case_revision,state_schema_version,sequence_no,story_time_seconds,player_place,player_x,player_y,player_z,opened_json,unlocked_json,visible_nodes_json,completed_operations_json,clues_json,facts_json,checks_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
  def insert(connection: Connection, row: GameSessionRecord): IO[Unit] = IO.blocking {
    require(row.stateSchemaVersion == 1 && row.sequence.value >= 0)
    Using.resource(connection.prepareStatement(insertSql)) { s =>
      s.setString(1,row.id.value)
      s.setString(2,row.owner.value)
      s.setString(3,row.caseId.value)
      s.setInt(4,row.caseRevision.value)
      s.setInt(5,row.stateSchemaVersion)
      s.setLong(6,row.sequence.value)
      s.setLong(7,row.storyTimeSeconds)
      s.setString(8,row.playerPlace.value)
      s.setDouble(9,row.playerPosition.x)
      s.setDouble(10,row.playerPosition.y)
      s.setDouble(11,row.playerPosition.z)
      s.setString(12,row.opened.asJson.noSpaces)
      s.setString(13,row.unlocked.asJson.noSpaces)
      s.setString(14,row.visibleNodes.asJson.noSpaces)
      s.setString(15,row.completedOperations.asJson.noSpaces)
      s.setString(16,row.clues.asJson.noSpaces)
      s.setString(17,row.facts.asJson.noSpaces)
      s.setString(18,row.externalChecks.asJson.noSpaces)
      s.executeUpdate()
    }
    ()
  }

  private val findSql = "SELECT * FROM game_sessions WHERE session_id=?"
  def find(connection: Connection, id: SessionId): IO[Option[GameSessionRecord]] = IO.blocking(query(connection,findSql,id))

  private val lockSql = "SELECT * FROM game_sessions WHERE session_id=? FOR UPDATE"
  def lock(connection: Connection, id: SessionId): IO[Option[GameSessionRecord]] = IO.blocking(query(connection,lockSql,id))

  private def query(connection: Connection, sql: String, id: SessionId): Option[GameSessionRecord] =
    Using.resource(connection.prepareStatement(sql)) { s =>
      s.setString(1,id.value)
      Using.resource(s.executeQuery())(r => if r.next() then Some(read(r)) else None)
    }

  private def read(r: ResultSet): GameSessionRecord =
    if r.getInt("state_schema_version") != 1 then throw GameError(ErrorCode.InvalidValue,"不支持的会话存储版本。")
    GameSessionRecord(SessionId(r.getString("session_id")),UserId(r.getString("owner_id")),CaseId(r.getString("case_id")),Revision(r.getInt("case_revision")),r.getInt("state_schema_version"),Sequence(r.getLong("sequence_no")),r.getLong("story_time_seconds"),PlaceId(r.getString("player_place")),Vec3(r.getDouble("player_x"),r.getDouble("player_y"),r.getDouble("player_z")),
      decode[Set[EntityId]](r.getString("opened_json")).fold(throw _,identity),
      decode[Set[EntityId]](r.getString("unlocked_json")).fold(throw _,identity),
      decode[Set[SceneNodeId]](r.getString("visible_nodes_json")).fold(throw _,identity),
      decode[Set[OperationId]](r.getString("completed_operations_json")).fold(throw _,identity),
      decode[Set[ClueId]](r.getString("clues_json")).fold(throw _,identity),
      decode[Set[FactId]](r.getString("facts_json")).fold(throw _,identity),
      decode[Vector[ExternalCheckProgress]](r.getString("checks_json")).fold(throw _,identity))

  private val updateSql = "UPDATE game_sessions SET state_schema_version=?,sequence_no=?,story_time_seconds=?,player_place=?,player_x=?,player_y=?,player_z=?,opened_json=?,unlocked_json=?,visible_nodes_json=?,completed_operations_json=?,clues_json=?,facts_json=?,checks_json=? WHERE session_id=? AND sequence_no=?"
  def update(connection: Connection, previous: GameSessionRecord, row: GameSessionRecord): IO[Unit] = IO.blocking {
    require(row.id==previous.id && row.owner==previous.owner && row.caseId==previous.caseId && row.caseRevision==previous.caseRevision && row.stateSchemaVersion==1 && row.sequence.value==previous.sequence.value+1)
    Using.resource(connection.prepareStatement(updateSql)) { s =>
      s.setInt(1,row.stateSchemaVersion)
      s.setLong(2,row.sequence.value)
      s.setLong(3,row.storyTimeSeconds)
      s.setString(4,row.playerPlace.value)
      s.setDouble(5,row.playerPosition.x)
      s.setDouble(6,row.playerPosition.y)
      s.setDouble(7,row.playerPosition.z)
      s.setString(8,row.opened.asJson.noSpaces)
      s.setString(9,row.unlocked.asJson.noSpaces)
      s.setString(10,row.visibleNodes.asJson.noSpaces)
      s.setString(11,row.completedOperations.asJson.noSpaces)
      s.setString(12,row.clues.asJson.noSpaces)
      s.setString(13,row.facts.asJson.noSpaces)
      s.setString(14,row.externalChecks.asJson.noSpaces)
      s.setString(15,row.id.value); s.setLong(16,previous.sequence.value)
      if s.executeUpdate()!=1 then throw GameError(ErrorCode.StaleState,"会话已发生变化。")
    }
  }
