package services.cases.api

import cats.effect.IO
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.cases.utils.LegacyDialogueKnowledge
import services.system.api.APIMessage
import services.system.objects.*

// New cases read personal data frozen in their own revision, without a character-name registry.
final case class GetDialogueKnowledgeMessage(caseId: CaseId, revision: Revision, witness: WitnessId)
  extends APIMessage[DialogueDossier]:
  override def plan(connection: Connection): IO[DialogueDossier] =
    for
      record <- CaseRevisionTable.find(connection,caseId,revision).flatMap(value =>
        IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件修订不存在。")))
      dossier = record.content.witnesses.find(_.id == witness).map(_.dossier)
        .orElse(record.content.police.filter(_.id == witness).map(_.dossier))
      result <- dossier match
        case Some(d) if d.knowledge.nonEmpty || d.privateContext.nonEmpty || d.policy.nonEmpty => IO.pure(d)
        case _ => LegacyDialogueKnowledge.load(caseId,revision,witness)
    yield result

object GetDialogueKnowledgeMessage:
  given Encoder[GetDialogueKnowledgeMessage] = deriveEncoder[GetDialogueKnowledgeMessage]
  given Decoder[GetDialogueKnowledgeMessage] = deriveDecoder[GetDialogueKnowledgeMessage]
