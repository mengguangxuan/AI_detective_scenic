package services.cases.api

import cats.effect.IO
import io.circe.parser.parse
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import java.nio.file.{Files,Paths}
import java.sql.Connection
import services.cases.objects.*
import services.system.api.APIMessage
import services.system.objects.*

// Internal author-maintained dialogue supplement. It adds personal context, not executable clues.
// Existing reservations retain their frozen context; existing case/session records are never rewritten.
final case class GetDialogueKnowledgeMessage(caseId: CaseId, revision: Revision, witness: WitnessId)
  extends APIMessage[DialogueDossier]:
  override def plan(connection: Connection): IO[DialogueDossier] =
    if caseId != CaseId("vicarage_study") || !Set(Revision(3),Revision(4),Revision(5))(revision) then
      IO.pure(DialogueDossier(Vector.empty,Vector.empty,Vector.empty))
    else IO.blocking {
      val json=parse(Files.readString(Paths.get("../docs/fixtures/vicarage_dialogue.json")))
        .fold(_ => throw GameError(ErrorCode.InvalidValue,"人物对话资料无法读取。"),identity)
      require(json.hcursor.get[Int]("version").contains(2),"Unsupported dialogue version")
      val entries=json.hcursor.get[Map[String,DialogueDossier]]("characters")
        .fold(_ => throw GameError(ErrorCode.InvalidValue,"人物对话资料格式不正确。"),identity)
      require(entries.keySet == Set("clement","mary","melchett","redding","marple"),"Invalid dialogue characters")
      require(entries.values.forall(d => d.knowledge.nonEmpty && Vector(d.knowledge,d.privateContext,d.policy).forall(xs =>
        xs.size <= 30 && xs.forall(s => s.trim.nonEmpty && s.length <= 1200))),"Invalid dialogue knowledge")
      entries.getOrElse(witness.value,DialogueDossier(Vector.empty,Vector.empty,Vector.empty))
    }

object GetDialogueKnowledgeMessage:
  given Encoder[GetDialogueKnowledgeMessage] = deriveEncoder[GetDialogueKnowledgeMessage]
  given Decoder[GetDialogueKnowledgeMessage] = deriveDecoder[GetDialogueKnowledgeMessage]
