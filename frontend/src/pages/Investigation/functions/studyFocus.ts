import type { SceneSelection } from '../objects/SceneSelection';
import { studyNodes, type StudyFocus } from '../objects/StudyFocus';
import { studyPoints } from '../objects/StudyPoints';
// Closed registry of this authored scene's camera presets, not a taxonomy of real objects.
export function studyFocus(selection: SceneSelection): StudyFocus {
  if (selection.kind === 'point') return selection.id === studyPoints.drawer ? 'drawer' : selection.id === studyPoints.letter ? 'letter' : 'overview';
  if (selection.kind === 'observation') {
    for (const key of ['desk', 'lamp', 'window', 'bookshelf'] as const)
      if (studyNodes[key] === selection.node) return key;
  }
  return 'overview';
}
