package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class InvestigationLogEntry(id: RecordId, sequence: Sequence, storyTimeLabel: String, text: String, clueIds: Set[ClueId])
object InvestigationLogEntry:
  given Encoder[InvestigationLogEntry] = deriveEncoder[InvestigationLogEntry]
  given Decoder[InvestigationLogEntry] = deriveDecoder[InvestigationLogEntry]
