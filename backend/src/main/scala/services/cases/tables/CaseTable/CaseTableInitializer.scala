package services.cases.tables.CaseTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object CaseTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS cases (
    case_id VARCHAR(200) PRIMARY KEY, content_schema_version INT NOT NULL CHECK(content_schema_version=1),
    title VARCHAR(500) NOT NULL, body_json CLOB NOT NULL)"""
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
