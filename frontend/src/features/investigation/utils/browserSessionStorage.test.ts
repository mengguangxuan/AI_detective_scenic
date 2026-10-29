import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadBrowserSession } from './browserSessionStorage';

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
