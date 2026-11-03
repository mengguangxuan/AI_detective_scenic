package services.cases.api

import cats.effect.IO
import java.nio.file.{Files,Path}
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseTable.CaseTable
import services.cases.utils.FixtureLoader
import services.system.api.APIMessage
import services.system.objects.*

// Internal authoring entry. The full source document is validated/projected before storage.
final case class ImportCaseMessage(path: Path) extends APIMessage[CaseRecord]:
  override def plan(connection: Connection): IO[CaseRecord] =
    for
      raw <- IO.blocking(Files.readString(path))
      record <- IO.fromEither(FixtureLoader.decode(raw))
      existing <- CaseTable.find(connection,record.caseId)
      _ <- existing match
        case None => CaseTable.insert(connection,record)
        case Some(saved) if saved==record => IO.unit
        case Some(_) => IO.raiseError(GameError(ErrorCode.ConflictingContent,"同一案件的内容不能覆盖。"))
    yield record
