import { describe, expect, it } from 'vitest';
import { investigationSnapshotSchema } from '../../../apis/games/objects/InvestigationSnapshot';
import { browserSessionSchema } from '../objects/BrowserSession';
import { apiName } from '../../../apiClient/apiName';
import { acceptSnapshot } from './acceptSnapshot';
const wire = { sessionId: 'session-one', caseId: 'study', caseRevision: { value: 1 }, sequence: { value: 1 }, title: '测试',
  scene: { bundleId: 'study', revision: '1', placeName: '书房', drawerOpened: true, letterVisible: true }, points: [], clues: [], journal: [] };
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
