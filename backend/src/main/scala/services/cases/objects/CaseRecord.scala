package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*


final case class CaseRecord(caseId: CaseId, contentSchemaVersion: Int, title: String, content: CaseContent)

object CaseRecord:
  given Encoder[CaseRecord] = deriveEncoder[CaseRecord]
  given Decoder[CaseRecord] = deriveDecoder[CaseRecord]
