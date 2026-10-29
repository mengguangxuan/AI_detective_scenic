import type { ClueId } from '../../../objects/ClueId';
import type { TimeAssessment } from '../../../apis/games/objects/TimeAssessment';
export type TimeComparisonDraft = { evidence: readonly ClueId[]; time: string;
  assessment: TimeAssessment | ''; attempted: boolean };
export const emptyTimeComparisonDraft: TimeComparisonDraft = { evidence: [], time: '', assessment: '', attempted: false };
