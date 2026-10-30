package services.cases.utils

import cats.effect.IO
import io.circe.parser.parse
import java.nio.file.{Files,Paths}
import services.cases.objects.*
import services.system.objects.*

// Compatibility only: revisions published before personal data was frozen in case content.
private[cases] object LegacyDialogueKnowledge:
  private[cases] def load(caseId: CaseId, revision: Revision, witness: WitnessId): IO[DialogueDossier] =
    val empty = DialogueDossier(Vector.empty,Vector.empty,Vector.empty)
    if caseId != CaseId("vicarage_study") || revision.value < 3 || revision.value > 9 then IO.pure(empty)
    else IO.blocking {
      val json = parse(Files.readString(Paths.get("../docs/fixtures/vicarage_dialogue.json")))
        .fold(_ => throw GameError(ErrorCode.InvalidValue,"旧人物资料无法读取。"),identity)
      require(json.hcursor.get[Int]("version").contains(2),"Unsupported legacy dialogue version")
      val entries = json.hcursor.get[Map[String,DialogueDossier]]("characters")
        .fold(_ => throw GameError(ErrorCode.InvalidValue,"旧人物资料格式不正确。"),identity)
      require(entries.values.forall(CharacterConfiguration.validDossier(_,true)),"Invalid legacy dialogue knowledge")
      entries.getOrElse(witness.value,empty)
    }
