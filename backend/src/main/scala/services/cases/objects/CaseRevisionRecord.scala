package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*


final case class CaseRevisionRecord(caseId: CaseId, revision: Revision, contentSchemaVersion: Int, title: String, content: CaseContent)

object CaseRevisionRecord:
  given Encoder[CaseRevisionRecord] = deriveEncoder[CaseRevisionRecord]
  given Decoder[CaseRevisionRecord] = deriveDecoder[CaseRevisionRecord]
