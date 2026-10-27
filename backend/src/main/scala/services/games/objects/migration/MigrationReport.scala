package services.games.objects.migration


final case class MigrationReport(sessions: Int, records: Int, receipts: Int)

object MigrationReport:
  given io.circe.Encoder[MigrationReport]=io.circe.generic.semiauto.deriveEncoder[MigrationReport]
  given io.circe.Decoder[MigrationReport]=io.circe.generic.semiauto.deriveDecoder[MigrationReport]
