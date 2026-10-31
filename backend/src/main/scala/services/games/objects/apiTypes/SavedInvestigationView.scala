package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.CaseId
import services.games.objects.SavedInvestigationStatus
import services.system.objects.{Revision, Sequence, SessionId}

final case class SavedInvestigationView(sessionId: SessionId, caseId: CaseId, revision: Revision, title: String,
  sequence: Sequence, updatedAtMillis: Option[Long], status: SavedInvestigationStatus)
object SavedInvestigationView:
  given Encoder[SavedInvestigationView] = deriveEncoder[SavedInvestigationView]
  given Decoder[SavedInvestigationView] = deriveDecoder[SavedInvestigationView]
