package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class InvestigationSnapshot(sessionId: SessionId, caseId: CaseId, caseRevision: Revision, sequence: Sequence, title: String, scene: StudySceneView, points: Vector[InquiryPointView], clues: Vector[ClueView], journal: Vector[InvestigationLogEntry])
object InvestigationSnapshot:
  given Encoder[InvestigationSnapshot] = deriveEncoder[InvestigationSnapshot]
  given Decoder[InvestigationSnapshot] = deriveDecoder[InvestigationSnapshot]
