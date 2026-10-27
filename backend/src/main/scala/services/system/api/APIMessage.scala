package services.system.api

import cats.effect.IO
import java.sql.Connection

trait APIMessage[Response]:
  def plan(connection: Connection): IO[Response]
