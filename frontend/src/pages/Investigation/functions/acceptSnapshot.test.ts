import { describe, expect, it } from 'vitest';
import { investigationSnapshotSchema } from '../../../apis/games/objects/InvestigationSnapshot';
import { browserSessionSchema } from '../objects/BrowserSession';
import { apiName } from '../../../apiClient/apiName';
import { retainSelection } from './retainSelection';
import type { SceneSelection } from '../objects/SceneSelection';
import { acceptSnapshot } from './acceptSnapshot';
const wire = { sessionId: 'session-one', caseId: 'study', caseRevision: { value: 1 }, sequence: { value: 1 }, title: '测试',
  scene: { bundleId: 'study', revision: '2', placeName: '书房', drawerOpened: true, letterVisible: true }, points: [], clues: [], journal: [], observations: [] };
describe('server-view mapping', () => {
  it('decodes Scala value wrappers and rejects unsafe sequence numbers', () => {
    expect(investigationSnapshotSchema.parse(wire).sequence).toBe(1);
    expect(investigationSnapshotSchema.safeParse({ ...wire, sequence: { value: Number.MAX_SAFE_INTEGER + 1 } }).success).toBe(false);
    expect(investigationSnapshotSchema.safeParse({ ...wire, scene: { ...wire.scene, bundleId: 'unknown-code' } }).success).toBe(false);
  });
  it('does not replace newer state with an old receipt-related snapshot', () => {
    const previous = investigationSnapshotSchema.parse(wire);
    const stale = investigationSnapshotSchema.parse({ ...wire, sequence: { value: 0 } });
    expect(acceptSnapshot(previous, stale)).toBe(previous);
    expect(() => acceptSnapshot(previous, { ...stale, caseId: 'another-case' })).toThrow();
  });
  it('keeps pending payload attached to its original session', () => {
    const saved = { version: 1, sessionId: 'one', creationPending: false,
      pending: { resultSequence: null, command: { kind: 'execute', sessionId: 'two', requestId: 'request', expectedSequence: 0, operation: 'open' } } };
    expect(browserSessionSchema.safeParse(saved).success).toBe(false);
    expect(browserSessionSchema.safeParse({ ...saved, version: 2 }).success).toBe(false);
  });
  it('uses the same name convention as backend message registrations', () => {
    expect(apiName('ExecuteOperationAPI')).toBe('executeoperation');
    expect(apiName('GetInvestigationSnapshotMessage')).toBe('getinvestigationsnapshot');
  });
});

describe('selection after a scene state change', () => {
  it('clears a letter selection when closing the drawer hides its entry', () => {
    const opened = investigationSnapshotSchema.parse({ ...wire, points: [{ id: 'point_letter', label: '信件', operations: [], routes: [] }] });
    const selection: SceneSelection = { kind: 'point', id: opened.points[0]!.id };
    expect(retainSelection(selection, opened)).toBe(selection);
    expect(retainSelection(selection, investigationSnapshotSchema.parse(wire))).toEqual({ kind: 'none' });
  });
  it('keeps an ordinary object selection only while its public description exists', () => {
    const observed = investigationSnapshotSchema.parse({ ...wire, observations: [{ node: 'node_window', label: '窗户', description: '一扇窗。' }] });
    const selection: SceneSelection = { kind: 'observation', node: observed.observations[0]!.node };
    expect(retainSelection(selection, observed)).toBe(selection);
    expect(retainSelection(selection, investigationSnapshotSchema.parse(wire))).toEqual({ kind: 'none' });
  });
});
