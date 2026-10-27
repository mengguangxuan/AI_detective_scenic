package services.cases.api

import cats.effect.IO
import java.nio.file.{Files,Path}
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.cases.utils.FixtureLoader
import services.system.api.APIMessage
import services.system.objects.*

// Internal authoring entry. The full source document is validated/projected before storage.
final case class ImportCaseMessage(path: Path) extends APIMessage[CaseRevisionRecord]:
  override def plan(connection: Connection): IO[CaseRevisionRecord] =
    for
      raw <- IO.blocking(Files.readString(path))
      record <- IO.fromEither(FixtureLoader.decode(raw))
      existing <- CaseRevisionTable.find(connection,record.caseId,record.revision)
      _ <- existing match
        case None => CaseRevisionTable.insert(connection,record)
        case Some(saved) if saved==record => IO.unit
        case Some(_) => IO.raiseError(GameError(ErrorCode.ConflictingContent,"同一案件修订的内容不能覆盖。"))
    yield record
