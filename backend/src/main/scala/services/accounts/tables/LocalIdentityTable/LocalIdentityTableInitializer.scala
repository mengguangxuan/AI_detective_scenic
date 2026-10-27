package services.accounts.tables.LocalIdentityTable
import cats.effect.IO
import java.sql.Connection
import scala.util.Using
object LocalIdentityTableInitializer:
  private val createSql = "CREATE TABLE IF NOT EXISTS local_identities (user_id VARCHAR(200) PRIMARY KEY, token_hash VARCHAR(64) NOT NULL UNIQUE)"
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    Using.resource(connection.createStatement())(_.execute(createSql))
    ()
  }
