package services.games.tables.ReceiptTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object ReceiptTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS game_receipts (
  session_id VARCHAR(200) NOT NULL REFERENCES game_sessions(session_id),
  request_id VARCHAR(200) NOT NULL,
  digest VARCHAR(64) NOT NULL,
  response CLOB NOT NULL,
  PRIMARY KEY (session_id, request_id)
)"""
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
