package services.games.tables.SessionRecordTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object SessionRecordTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS session_records (
 record_id VARCHAR(220) NOT NULL, session_id VARCHAR(200) NOT NULL REFERENCES game_sessions(session_id),
 request_id VARCHAR(200) NOT NULL, sequence_no BIGINT NOT NULL CHECK(sequence_no>0), story_time_seconds BIGINT NOT NULL,
 point_id VARCHAR(200) NOT NULL, route_id VARCHAR(200), operation_id VARCHAR(200), text CLOB NOT NULL,
 clues_json CLOB NOT NULL, facts_json CLOB NOT NULL, PRIMARY KEY(session_id,record_id), UNIQUE(session_id,sequence_no),
 CHECK((route_id IS NULL AND operation_id IS NOT NULL) OR (route_id IS NOT NULL AND operation_id IS NULL)))"""
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
