package services.cases.api

import cats.effect.IO
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseTable.CaseTable
import services.system.api.APIMessage
import services.system.objects.*
import services.cases.objects.dialogue.DialogueDossier

// New cases read personal data frozen in their own caseRecord, without a character-name registry.
final case class GetDialogueKnowledgeMessage(caseId: CaseId, witness: WitnessId)
  extends APIMessage[DialogueDossier]:
  override def plan(connection: Connection): IO[DialogueDossier] =
    for
      record <- CaseTable.find(connection,caseId).flatMap(value =>
        IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件不存在。")))
      dossier = record.content.witnesses.find(_.id == witness).map(_.dossier)
      result <- dossier match
        case Some(d) if d.knowledge.nonEmpty || d.privateContext.nonEmpty || d.policy.nonEmpty => IO.pure(d)
        case _ => IO.pure(DialogueDossier(Vector.empty,Vector.empty,Vector.empty))
    yield result

object GetDialogueKnowledgeMessage:
  given Encoder[GetDialogueKnowledgeMessage] = deriveEncoder[GetDialogueKnowledgeMessage]
  given Decoder[GetDialogueKnowledgeMessage] = deriveDecoder[GetDialogueKnowledgeMessage]
