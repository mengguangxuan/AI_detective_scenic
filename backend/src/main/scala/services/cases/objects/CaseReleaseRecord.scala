package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

// Publication metadata is explicit; the full author document is never a catalogue response.
final case class CaseReleaseRecord(caseId: CaseId, summary: String, tags: Vector[String], sceneBundle: String)
object CaseReleaseRecord:
  given Encoder[CaseReleaseRecord] = deriveEncoder[CaseReleaseRecord]
  given Decoder[CaseReleaseRecord] = deriveDecoder[CaseReleaseRecord]
