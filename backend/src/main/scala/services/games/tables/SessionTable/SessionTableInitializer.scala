package services.games.tables.SessionTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object SessionTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS game_sessions (
 session_id VARCHAR(200) PRIMARY KEY, owner_id VARCHAR(200) NOT NULL,
 case_id VARCHAR(200) NOT NULL, case_revision INT NOT NULL,
 state_schema_version INT NOT NULL CHECK(state_schema_version=1), sequence_no BIGINT NOT NULL CHECK(sequence_no>=0),
 story_time_seconds BIGINT NOT NULL, player_place VARCHAR(200) NOT NULL,
 player_x DOUBLE PRECISION NOT NULL, player_y DOUBLE PRECISION NOT NULL, player_z DOUBLE PRECISION NOT NULL,
 opened_json CLOB NOT NULL, unlocked_json CLOB NOT NULL, visible_nodes_json CLOB NOT NULL,
 completed_operations_json CLOB NOT NULL, clues_json CLOB NOT NULL, facts_json CLOB NOT NULL,
 FOREIGN KEY(case_id,case_revision) REFERENCES case_revisions(case_id,revision))"""
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
