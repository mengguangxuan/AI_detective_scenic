import type { PointId } from '../../../objects/PointId';
import type { SceneNodeId } from '../../../objects/SceneNodeId';
export type SceneSelection = { kind: 'none' } | { kind: 'point'; id: PointId } | { kind: 'observation'; node: SceneNodeId };
