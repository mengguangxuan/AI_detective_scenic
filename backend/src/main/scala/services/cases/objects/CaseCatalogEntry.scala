package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

final case class CaseCatalogEntry(caseId: CaseId, title: String, summary: String, tags: Vector[String])
object CaseCatalogEntry:
  given Encoder[CaseCatalogEntry] = deriveEncoder[CaseCatalogEntry]
  given Decoder[CaseCatalogEntry] = deriveDecoder[CaseCatalogEntry]
