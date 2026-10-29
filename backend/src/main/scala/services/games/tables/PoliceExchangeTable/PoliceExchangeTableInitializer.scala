package services.games.tables.PoliceExchangeTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object PoliceExchangeTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS police_exchanges (
 session_id VARCHAR(200) NOT NULL REFERENCES game_sessions(session_id), request_id VARCHAR(200) NOT NULL,
 sequence_no BIGINT NOT NULL CHECK(sequence_no>0), payload_json CLOB NOT NULL,
 PRIMARY KEY(session_id,request_id), UNIQUE(session_id,sequence_no))"""
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
