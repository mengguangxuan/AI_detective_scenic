package services.games.objects.apiTypes
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.Sequence
final case class StatementVersionView(clue: ClueView, sequence: Sequence, recordedAt: String)
object StatementVersionView:
  given Encoder[StatementVersionView] = deriveEncoder[StatementVersionView]
  given Decoder[StatementVersionView] = deriveDecoder[StatementVersionView]
