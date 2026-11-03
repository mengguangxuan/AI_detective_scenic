package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Paths}
import java.util.UUID
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.cases.tables.CaseTable.CaseTable
import services.games.api.*
import services.system.api.*
import services.system.objects.*

class CaseIdentitySuite extends FunSuite:
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A] =
    DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))

  test("case identity is the sole database key and is the only case locator in public views") {
    val url=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    val owner=UserId("owner");val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      imported <- tx(url)(ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan)
      _ <- tx(url)(c => services.TestCases.publish(c,imported.caseId))
      opened <- tx(url)(OpenInvestigationMessage(owner,id,imported.caseId).plan)
      retry <- tx(url)(OpenInvestigationMessage(owner,id,imported.caseId).plan)
      _ = assertEquals(retry,opened)
      _ = assertEquals(opened.caseId,imported.caseId)
      _ = assert(!opened.asJson.noSpaces.toLowerCase.contains("revision"))
      _ <- DatabaseSession.connection(url).use(c => IO.blocking {
        val keys=c.getMetaData.getPrimaryKeys(null,"PUBLIC","CASES")
        val columns=Vector.newBuilder[String]
        try { while keys.next() do columns += keys.getString("COLUMN_NAME") } finally keys.close()
        assertEquals(columns.result(),Vector("CASE_ID"))
        val tables=c.getMetaData.getTables(null,"PUBLIC","CASE_REVISIONS",Array("TABLE"))
        try assert(!tables.next()) finally tables.close()
      })
    yield ()).unsafeRunSync()
  }

  test("imports are idempotent by case identity and conflicting content cannot overwrite an active investigation") {
    val url=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    val path=Files.createTempFile("case-conflict-",".json")
    val source=parser.parse(Files.readString(Paths.get("../docs/fixtures/browser_investigation.json"))).toOption.get
    (for
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      _ <- IO.blocking(Files.writeString(path,source.noSpaces))
      original <- tx(url)(ImportCaseMessage(path).plan)
      repeated <- tx(url)(ImportCaseMessage(path).plan)
      _ = assertEquals(repeated,original)
      _ <- IO.blocking(Files.writeString(path,source.mapObject(_.add("title",Json.fromString("changed"))).noSpaces))
      conflict <- tx(url)(ImportCaseMessage(path).plan).attempt
      _ = assertEquals(conflict.left.toOption.collect { case e: GameError => e.code },Some(ErrorCode.ConflictingContent))
      stored <- tx(url)(CaseTable.find(_,original.caseId))
      _ = assertEquals(stored,Some(original))
    yield ()).guarantee(IO.blocking(Files.deleteIfExists(path)).void).unsafeRunSync()
  }
