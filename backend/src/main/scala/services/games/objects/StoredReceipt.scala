package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.games.objects.apiTypes.*

final case class StoredReceipt(digest: String, response: CommandResult)

object StoredReceipt:
  given Encoder[StoredReceipt] = deriveEncoder[StoredReceipt]
  given Decoder[StoredReceipt] = deriveDecoder[StoredReceipt]
