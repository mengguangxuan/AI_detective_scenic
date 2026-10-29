import type { InvestigationSnapshot } from '../../../apis/games/objects/InvestigationSnapshot';
import type { SceneSelection } from '../objects/SceneSelection';
export function retainSelection(selection: SceneSelection, snapshot: InvestigationSnapshot): SceneSelection {
  if (selection.kind === 'point' && snapshot.points.some(point => point.id === selection.id)) return selection;
  if (selection.kind === 'observation' && snapshot.observations.some(item => item.node === selection.node)) return selection;
  return { kind: 'none' };
}
