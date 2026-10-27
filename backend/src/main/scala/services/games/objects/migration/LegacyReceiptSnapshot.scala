package services.games.objects.migration

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.games.objects.apiTypes.CommandResult


final case class LegacyReceiptSnapshot(sessionId: SessionId, requestId: RequestId, digest: String, result: CommandResult)

object LegacyReceiptSnapshot:
  given Encoder[LegacyReceiptSnapshot] = deriveEncoder[LegacyReceiptSnapshot]
  given Decoder[LegacyReceiptSnapshot] = deriveDecoder[LegacyReceiptSnapshot]
