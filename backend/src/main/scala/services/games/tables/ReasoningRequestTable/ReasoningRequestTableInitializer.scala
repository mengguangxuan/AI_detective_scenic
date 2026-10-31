package services.games.tables.ReasoningRequestTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object ReasoningRequestTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS reasoning_requests (
 session_id VARCHAR(200) NOT NULL REFERENCES game_sessions(session_id), request_id VARCHAR(200) NOT NULL,
 digest VARCHAR(64) NOT NULL, lease_token VARCHAR(64) NOT NULL, lease_until BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('Pending','Finished','Cancelled')), payload_json CLOB NOT NULL,
 PRIMARY KEY(session_id,request_id))"""
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
