package services.games.tables.SessionTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object SessionTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS game_sessions (
  session_id VARCHAR(200) PRIMARY KEY,
  owner_id VARCHAR(200) NOT NULL,
  body CLOB NOT NULL,
  sequence_no BIGINT NOT NULL CHECK (sequence_no >= 0)
)"""
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
