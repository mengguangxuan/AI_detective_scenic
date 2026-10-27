package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class CommandResult(sequence: Sequence, findings: Vector[Text], acquiredClues: Set[ClueId])

object CommandResult:
  given Encoder[CommandResult] = deriveEncoder[CommandResult]
  given Decoder[CommandResult] = deriveDecoder[CommandResult]
