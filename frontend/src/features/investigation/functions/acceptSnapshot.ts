import type { InvestigationSnapshot } from '../../../apis/games/objects/InvestigationSnapshot';
export function acceptSnapshot(previous: InvestigationSnapshot | null, next: InvestigationSnapshot): InvestigationSnapshot {
  if (previous && (previous.sessionId !== next.sessionId || previous.caseId !== next.caseId || previous.caseRevision !== next.caseRevision))
    throw new Error('服务器返回了不同会话或案件修订。');
  return previous && previous.sequence > next.sequence ? previous : next;
}
