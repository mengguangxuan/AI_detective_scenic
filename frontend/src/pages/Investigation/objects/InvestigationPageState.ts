import type { InvestigationSnapshot } from '../../../apis/games/objects/InvestigationSnapshot';
import type { SceneSelection } from './SceneSelection';
import type { PendingCommand } from './PendingCommand';
export type InvestigationPageState =
  | { kind: 'loading' }
  | { kind: 'failed'; message: string }
  | { kind: 'ready'; snapshot: InvestigationSnapshot; selected: SceneSelection; notice: string;
      mutation: { kind: 'idle' } | { kind: 'submitting' } | { kind: 'outcomeUnknown'; pending: PendingCommand; problem: string }
        | { kind: 'awaitingSnapshot'; problem: string } };
