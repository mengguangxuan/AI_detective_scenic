package services.games.objects

import io.circe.{Decoder, Encoder}

enum SavedInvestigationStatus:
  case Investigating, Established, Solved, Revealed, LegacySubmitted
object SavedInvestigationStatus:
  given Encoder[SavedInvestigationStatus] = Encoder.encodeString.contramap(_.toString)
  given Decoder[SavedInvestigationStatus] = Decoder.decodeString.emap(value =>
    SavedInvestigationStatus.values.find(_.toString == value).toRight("Unknown investigation status"))
