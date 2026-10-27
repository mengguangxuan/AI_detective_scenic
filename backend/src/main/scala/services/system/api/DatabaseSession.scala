package services.system.api

import cats.effect.{IO, Resource}
import java.sql.{Connection, DriverManager}

// Infrastructure boundary, not a forwarding business Service.
object DatabaseSession:
  def connection(url: String): Resource[IO, Connection] =
    Resource.fromAutoCloseable(IO.blocking(DriverManager.getConnection(url)))

  // A JDBC connection must not be shared by concurrent messages.
  def transaction[A](connection: Connection)(program: IO[A]): IO[A] =
    IO.uncancelable { poll =>
      for
        _ <- IO.blocking {
          require(connection.getAutoCommit, "Nested transactions are not supported")
          connection.setAutoCommit(false)
        }
        value <- poll(program).flatMap(result => IO.blocking(connection.commit()).as(result))
          .onCancel(IO.blocking(connection.rollback()))
          .handleErrorWith(error => IO.blocking(connection.rollback()) *> IO.raiseError[A](error))
          .guarantee(IO.blocking(connection.setAutoCommit(true)))
      yield value
    }
