package services.games.tables.InterviewTurnTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object InterviewTurnTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS interview_turns (
 session_id VARCHAR(200) NOT NULL REFERENCES game_sessions(session_id), request_id VARCHAR(200) NOT NULL,
 sequence_no BIGINT NOT NULL CHECK(sequence_no>0), witness_id VARCHAR(100) NOT NULL, payload_json CLOB NOT NULL,
 PRIMARY KEY(session_id,request_id), UNIQUE(session_id,sequence_no))"""
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
