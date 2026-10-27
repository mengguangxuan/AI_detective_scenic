package services.cases.api

import cats.effect.IO
import java.nio.file.{Files, Path}
import java.sql.Connection
import services.cases.objects.InvestigationFixture
import services.cases.utils.FixtureLoader
import services.system.api.APIMessage

// Internal authoring input, never registered as an HTTP endpoint.
final case class LoadFixtureMessage(path: Path) extends APIMessage[InvestigationFixture]:
  override def plan(connection: Connection): IO[InvestigationFixture] =
    IO.blocking(Files.readString(path)).flatMap(raw => IO.fromEither(FixtureLoader.decode(raw)))
