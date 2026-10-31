package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.Revision

final case class CaseCatalogEntry(caseId: CaseId, revision: Revision, title: String, summary: String, tags: Vector[String])
object CaseCatalogEntry:
  given Encoder[CaseCatalogEntry] = deriveEncoder[CaseCatalogEntry]
  given Decoder[CaseCatalogEntry] = deriveDecoder[CaseCatalogEntry]
