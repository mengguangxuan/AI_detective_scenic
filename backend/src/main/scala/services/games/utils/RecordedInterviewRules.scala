package services.games.utils

import services.cases.objects.*
import services.games.objects.*

private[games] object RecordedInterviewRules:
  // A material retains its first acquired wording; later dialogue never rewrites a citation.
  private[games] def content(source: CaseContent, turns: Vector[InterviewTurnRecord]): CaseContent =
    val entries=turns.filter(_.mode == InterviewReplyMode.Answered).sortBy(_.sequence.value).flatMap { turn =>
      val witness=source.witnesses.find(_.id == turn.witness)
      turn.recognition.toVector.flatMap(_.claims).map(c => c.clue -> c.text) ++ turn.statementUpdates.flatMap(update => witness.toVector.flatMap(_.answers.filter(_.id == update.answerId))
        .flatMap(_.routes).flatMap(id => source.routes.filter(_.id == id)).flatMap(_.clues).map(_ -> update.text))
    }
    val first=entries.reverse.toMap
    if first.isEmpty then source else source.copy(clues=source.clues.map(c => first.get(c.id).fold(c)(text => c.copy(text=services.system.objects.Text(text)))))

  private[games] def ownClues(content: CaseContent,witness: WitnessId,records: Vector[SessionRecord],turns: Vector[InterviewTurnRecord]): Set[ClueId] =
    val person=content.witnesses.find(_.id == witness)
    val fixed=records.filter(r => person.exists(_.point == r.point)).flatMap(_.disclosedClues)
    val spoken=turns.filter(t => t.witness == witness && t.mode == InterviewReplyMode.Answered).flatMap(_.answerIds)
      .flatMap(id => person.toVector.flatMap(_.answers.filter(_.id == id))).flatMap(_.routes)
      .flatMap(id => content.routes.filter(_.id == id)).flatMap(_.clues)
    fixed.toSet ++ spoken ++ turns.filter(t => t.witness == witness && t.mode == InterviewReplyMode.Answered).flatMap(_.recognition.toVector.flatMap(_.claims.map(_.clue)))
