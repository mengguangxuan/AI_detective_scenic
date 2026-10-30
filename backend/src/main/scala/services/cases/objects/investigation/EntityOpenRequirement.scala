package services.cases.objects.investigation
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.EntityId
final case class EntityOpenRequirement(entity: EntityId, opened: Boolean)
object EntityOpenRequirement:
  given Encoder[EntityOpenRequirement] = deriveEncoder[EntityOpenRequirement]
  given Decoder[EntityOpenRequirement] = deriveDecoder[EntityOpenRequirement]
