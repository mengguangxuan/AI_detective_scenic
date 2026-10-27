package services.cases.tables.CaseRevisionTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object CaseRevisionTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS case_revisions (
 case_id VARCHAR(200) NOT NULL, revision INT NOT NULL CHECK(revision>0),
 content_schema_version INT NOT NULL CHECK(content_schema_version=1), title VARCHAR(500) NOT NULL,
 body_json CLOB NOT NULL, PRIMARY KEY(case_id,revision))"""
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
