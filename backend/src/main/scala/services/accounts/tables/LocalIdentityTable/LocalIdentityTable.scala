package services.accounts.tables.LocalIdentityTable
import cats.effect.IO
import java.sql.Connection
import scala.util.Using
import services.accounts.objects.LocalIdentityRecord
import services.system.objects.UserId
object LocalIdentityTable:
  private val insertSql = "INSERT INTO local_identities(user_id,token_hash) VALUES (?,?)"
  def insert(connection: Connection, row: LocalIdentityRecord): IO[Unit] = IO.blocking {
    Using.resource(connection.prepareStatement(insertSql)) { s =>
      s.setString(1,row.userId.value)
      s.setString(2,row.tokenHash)
      s.executeUpdate()
    }
    ()
  }
  private val findSql = "SELECT user_id FROM local_identities WHERE token_hash=?"
  def findUser(connection: Connection, tokenHash: String): IO[Option[UserId]] = IO.blocking {
    Using.resource(connection.prepareStatement(findSql)) { s =>
      s.setString(1,tokenHash)
      Using.resource(s.executeQuery())(r => if r.next() then Some(UserId(r.getString("user_id"))) else None)
    }
  }
