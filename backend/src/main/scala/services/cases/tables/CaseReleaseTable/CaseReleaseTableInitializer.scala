package services.cases.tables.CaseReleaseTable

import cats.effect.IO
import java.sql.Connection
import scala.util.Using

object CaseReleaseTableInitializer:
  private val createSql = """CREATE TABLE IF NOT EXISTS case_releases (
    case_id VARCHAR(200) NOT NULL, revision INT NOT NULL, summary VARCHAR(2000) NOT NULL,
    tags_json CLOB NOT NULL, scene_bundle VARCHAR(100) NOT NULL, scene_revision VARCHAR(100) NOT NULL,
    PRIMARY KEY(case_id,revision), FOREIGN KEY(case_id,revision) REFERENCES case_revisions(case_id,revision))"""
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
