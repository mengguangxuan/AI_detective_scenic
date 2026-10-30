package services.cases.objects.dialogue

import io.circe.{Decoder, Encoder, DecodingFailure}
import io.circe.generic.semiauto.*
import services.cases.objects.PersonId

// Author-owned personal data. Entry points, disclosures and phase transitions belong elsewhere.
final case class CharacterProfile(id: PersonId, name: String, identity: String,
  victimRelation: String, caseRelation: String, background: String, voice: String, dossier: DialogueDossier)
object CharacterProfile:
  given Encoder[CharacterProfile] = deriveEncoder[CharacterProfile]
  private val fields = Set("id", "name", "identity", "victimRelation", "caseRelation", "background", "voice", "dossier")
  private val decoder: Decoder[CharacterProfile] = deriveDecoder[CharacterProfile]
  given Decoder[CharacterProfile] = Decoder.instance { cursor =>
    if cursor.value.asObject.exists(_.keys.toSet == fields) then decoder(cursor)
    else Left(DecodingFailure("人物资料字段缺失或混入交互配置", cursor.history))
  }
