package services.sceneproduction.tables.SceneProductionTable
import cats.effect.IO
import java.sql.Connection
import scala.util.Using
object SceneProductionTableInitializer:
  def initialize(c: Connection): IO[Unit] = IO.blocking {
    Using.resource(c.createStatement())(_.executeUpdate("""CREATE TABLE IF NOT EXISTS scene_productions (
      production_id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(200) NOT NULL,
      input_json CLOB NOT NULL, phase VARCHAR(20) NOT NULL CHECK(phase IN ('Draft','Queued','Saved')),
      created_at VARCHAR(50) NOT NULL, saved_at VARCHAR(50),
      CHECK ((phase='Saved' AND saved_at IS NOT NULL) OR (phase<>'Saved' AND saved_at IS NULL))
    )"""))
    ()
  }
