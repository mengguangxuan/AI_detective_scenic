package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.games.objects.apiTypes.CommandResult


final case class RequestReceiptRecord(sessionId: SessionId, requestId: RequestId, digest: String, responseSchemaVersion: Int, result: CommandResult)

object RequestReceiptRecord:
  given Encoder[RequestReceiptRecord] = deriveEncoder[RequestReceiptRecord]
  given Decoder[RequestReceiptRecord] = deriveDecoder[RequestReceiptRecord]
