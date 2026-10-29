package services.cases.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

// Public opening information only; testimony and private motives do not belong here.
final case class CasePersonBrief(id: PersonId, name: String, identity: String,
  victimRelation: String, caseRelation: String, witness: Option[WitnessId])
object CasePersonBrief:
  given Encoder[CasePersonBrief] = deriveEncoder[CasePersonBrief]
  given Decoder[CasePersonBrief] = deriveDecoder[CasePersonBrief]
