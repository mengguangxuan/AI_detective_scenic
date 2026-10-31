import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadBrowserSession } from './browserSessionStorage';
import { newBrowserSession } from './investigationConnection';

const currentKey = 'detective-scenic:server-session:v6';
const previousKey = 'detective-scenic:server-session:v5';
let values: Map<string, string>;
beforeEach(() => {
  values = new Map();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value); },
  });
});
afterEach(() => vi.unstubAllGlobals());

describe('restoring across a published case revision', () => {
  it('freezes the selected case and revision for an opening retry and refuses switching an unresolved opening', () => {
    newBrowserSession('a-selected-case', 7);
    const saved = loadBrowserSession()!;
    expect(saved.creation).toEqual({ caseId: 'a-selected-case', revision: 7 });
    expect(saved.creationPending).toBe(true);
    expect(() => newBrowserSession('another-case', 8)).toThrow();
    expect(loadBrowserSession()).toEqual(saved);
  });
  it('does not replace an unresolved mutation when selecting another case', () => {
    const saved = { version: 1, sessionId: 'old-session', creationPending: false, pending: { resultSequence: null,
      command: { kind: 'investigate', sessionId: 'old-session', requestId: 'original-request', expectedSequence: 2, point: 'point_clock', route: 'route_clock' } } };
    values.set(currentKey, JSON.stringify(saved));
    expect(() => newBrowserSession('another-case', 8)).toThrow();
    expect(loadBrowserSession()).toEqual(saved);
  });
  it('preserves the exact unresolved command and the previous locator backup', () => {
    const saved = { version: 1, sessionId: 'old-session', creationPending: false,
      pending: { resultSequence: null, command: { kind: 'investigate', sessionId: 'old-session',
        requestId: 'original-request', expectedSequence: 2, point: 'point_clock', route: 'route_clock' } } };
    values.set(previousKey, JSON.stringify(saved));
    expect(loadBrowserSession()).toEqual(saved);
    expect(JSON.parse(values.get(currentKey)!)).toEqual(saved);
    expect(JSON.parse(values.get(previousKey)!)).toEqual(saved);
  });
  it('does not replace a current session with an older backup', () => {
    const current = { version: 1, sessionId: 'current-session', creationPending: false, pending: null };
    values.set(currentKey, JSON.stringify(current));
    values.set(previousKey, JSON.stringify({ ...current, sessionId: 'old-session' }));
    expect(loadBrowserSession()).toEqual(current);
  });
  it('does not hide an invalid current locator by silently starting or loading another session', () => {
    values.set(currentKey, 'invalid-json');
    values.set(previousKey, JSON.stringify({ version: 1, sessionId: 'old-session', creationPending: false, pending: null }));
    expect(() => loadBrowserSession()).toThrow();
    expect(values.get(currentKey)).toBe('invalid-json');
  });
});
