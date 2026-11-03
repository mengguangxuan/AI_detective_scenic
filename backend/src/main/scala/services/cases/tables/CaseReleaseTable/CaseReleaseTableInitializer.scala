package services.cases.tables.CaseReleaseTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object CaseReleaseTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS case_releases (
    case_id VARCHAR(200) PRIMARY KEY REFERENCES cases(case_id), summary VARCHAR(2000) NOT NULL,
    tags_json CLOB NOT NULL, scene_bundle VARCHAR(100) NOT NULL)"""
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
