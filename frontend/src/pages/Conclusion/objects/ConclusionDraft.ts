import type { PersonId } from '../../../objects/PersonId';
import type { ClueId } from '../../../objects/ClueId';
export type ConclusionDraft = { suspects: PersonId[]; evidence: ClueId[]; explanation: string; reveal: boolean };
export const emptyConclusionDraft: ConclusionDraft = { suspects: [], evidence: [], explanation: '', reveal: false };
