package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.CaseId
import services.system.objects.{Revision, Sequence, SessionId}

final case class InvestigationListRecord(sessionId: SessionId, caseId: CaseId, revision: Revision, title: String,
  sequence: Sequence, updatedAtMillis: Option[Long], reasoning: ReasoningProgress, legacySubmitted: Boolean)
object InvestigationListRecord:
  given Encoder[InvestigationListRecord] = deriveEncoder[InvestigationListRecord]
  given Decoder[InvestigationListRecord] = deriveDecoder[InvestigationListRecord]
