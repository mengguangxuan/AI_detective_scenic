import type { InvestigationSnapshot } from '../../apis/games/objects/InvestigationSnapshot';
import { investigationPages } from '../objects/InvestigationPage';
export function availableInvestigationPages(snapshot: InvestigationSnapshot | null) {
  return investigationPages.filter(page => {
    switch (page.path) {
      case '/police': return snapshot?.police != null;
      case '/interviews': return (snapshot?.witnesses.length ?? 0) > 0;
      case '/people': return (snapshot?.people.length ?? 0) > 0;
      case '/map': return snapshot?.areaMap != null || (snapshot?.scene.travel?.length ?? 0) > 0;
      case '/conclusion': return (snapshot?.conclusionCandidates.length ?? 0) > 0 || snapshot?.conclusion != null || snapshot?.reasoning != null;
      default: return true;
    }
  });
}
