package services.games.tables.SessionRecordTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object SessionRecordTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS session_records (
 record_id VARCHAR(220) NOT NULL, session_id VARCHAR(200) NOT NULL REFERENCES game_sessions(session_id),
 request_id VARCHAR(200) NOT NULL, sequence_no BIGINT NOT NULL CHECK(sequence_no>0), story_time_seconds BIGINT NOT NULL,
 point_id VARCHAR(200), route_id VARCHAR(200), operation_id VARCHAR(200), text CLOB NOT NULL,
 clues_json CLOB NOT NULL, facts_json CLOB NOT NULL, system_action VARCHAR(40),
 PRIMARY KEY(session_id,record_id), UNIQUE(session_id,sequence_no))"""
  private val contextCheckSql = """ALTER TABLE session_records ADD CONSTRAINT IF NOT EXISTS record_context_check CHECK (
 (system_action IS NULL AND point_id IS NOT NULL AND
  ((route_id IS NULL AND operation_id IS NOT NULL) OR (route_id IS NOT NULL AND operation_id IS NULL))) OR
 (system_action IS NOT NULL AND system_action IN ('Wait','EvaluateReasoning','SubmitConclusion','RevealReasoning') AND
  point_id IS NULL AND route_id IS NULL AND operation_id IS NULL))"""
  private val oldChecksSql = """SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE
 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
 ON tc.CONSTRAINT_CATALOG=cc.CONSTRAINT_CATALOG AND tc.CONSTRAINT_SCHEMA=cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME=cc.CONSTRAINT_NAME
 WHERE tc.TABLE_SCHEMA='PUBLIC' AND tc.TABLE_NAME='SESSION_RECORDS' AND tc.CONSTRAINT_TYPE='CHECK'"""

  // Evolve the current record table without clearing investigations or changing their receipts.
  // Earlier system records used reserved synthetic route IDs; the request-derived suffix identifies them exactly.
  private def evolveContext(connection: Connection): Unit =
    Using.resource(connection.createStatement()) { statement =>
      statement.execute("ALTER TABLE session_records ADD COLUMN IF NOT EXISTS system_action VARCHAR(40)")
      statement.execute("ALTER TABLE session_records ALTER COLUMN point_id DROP NOT NULL")
      val oldChecks=Using.resource(statement.executeQuery(oldChecksSql)) { rows =>
        val result=Vector.newBuilder[String]
        while rows.next() do
          val clause=rows.getString("CHECK_CLAUSE").toUpperCase(java.util.Locale.ROOT)
          if clause.contains("ROUTE_ID") && clause.contains("OPERATION_ID") && !clause.contains("SYSTEM_ACTION") then result += rows.getString("CONSTRAINT_NAME")
        result.result()
      }
      oldChecks.foreach(name => statement.execute(s"ALTER TABLE session_records DROP CONSTRAINT \"${name.replace("\"","\"\"")}\""))
      Vector(("advance_time","wait","Wait"),("evaluate_reasoning","reasoning","EvaluateReasoning"),
        ("submit_conclusion","conclusion","SubmitConclusion"),("reveal_reasoning","reasoning-ending","RevealReasoning")).foreach { (route,suffix,action) =>
        Using.resource(connection.prepareStatement("UPDATE session_records SET point_id=NULL, route_id=NULL, system_action=? WHERE system_action IS NULL AND operation_id IS NULL AND route_id=? AND record_id=request_id || ?")) { update =>
          update.setString(1,action); update.setString(2,route); update.setString(3,s":$suffix"); update.executeUpdate()
        }
      }
      statement.execute(contextCheckSql)
    }

  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    evolveContext(connection)
  }
