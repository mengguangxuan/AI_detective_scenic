package services.cases.objects
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
final case class EntityOpenRequirement(entity: EntityId, opened: Boolean)
object EntityOpenRequirement:
  given Encoder[EntityOpenRequirement] = deriveEncoder[EntityOpenRequirement]
  given Decoder[EntityOpenRequirement] = deriveDecoder[EntityOpenRequirement]
