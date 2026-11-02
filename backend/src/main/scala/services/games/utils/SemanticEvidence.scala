package services.games.utils

import services.cases.objects.*
import services.games.objects.GameSessionRecord

// Equivalence is author-declared shared propositions, never guessed from titles or words.
private[games] object SemanticEvidence:
  private[games] def resolve(content: CaseContent, required: Set[ClueId], available: Set[ClueId]): Option[Set[ClueId]] =
    val resolved=required.toVector.map { id =>
      if available(id) then Some(id) else content.clues.find(_.id == id).filter(_.facts.nonEmpty).flatMap { target =>
        content.clues.find(c => available(c.id) && target.facts.subsetOf(c.facts)).map(_.id)
      }
    }
    if resolved.forall(_.nonEmpty) then Some(resolved.flatten.toSet) else None

  private[games] def forChecks(content: CaseContent,state: GameSessionRecord): GameSessionRecord =
    if content.interviewPromptVersion < 3 then state else
      state.copy(clues=state.clues ++ content.clues.filter(c => resolve(content,Set(c.id),state.clues).nonEmpty).map(_.id))
