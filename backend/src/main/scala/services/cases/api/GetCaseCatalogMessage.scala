package services.cases.api

import cats.effect.IO
import java.sql.Connection
import services.cases.objects.CaseCatalogEntry
import services.cases.tables.CaseReleaseTable.CaseReleaseTable
import services.system.api.APIMessage

final case class GetCaseCatalogMessage() extends APIMessage[Vector[CaseCatalogEntry]]:
  override def plan(connection: Connection): IO[Vector[CaseCatalogEntry]] = CaseReleaseTable.catalog(connection)
