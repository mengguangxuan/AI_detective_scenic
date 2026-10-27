package services.games.tables.CommitTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object CommitTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS game_commits (
  session_id VARCHAR(200) NOT NULL REFERENCES game_sessions(session_id),
  sequence_no BIGINT NOT NULL CHECK (sequence_no > 0),
  body CLOB NOT NULL,
  PRIMARY KEY (session_id, sequence_no)
)"""
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
