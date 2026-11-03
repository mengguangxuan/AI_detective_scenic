import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadBrowserSession, browserSessionKey } from './browserSessionStorage';
import { newBrowserSession } from './investigationConnection';
let values: Map<string, string>;
beforeEach(() => {
  values = new Map();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value); },
    removeItem: (key: string) => { values.delete(key); },
  });
});
afterEach(() => vi.unstubAllGlobals());

describe('case-only opening and current investigation recovery', () => {
  it('preserves the selected case on retry and refuses replacing an unresolved opening', () => {
    newBrowserSession('selected-case');
    const saved = loadBrowserSession()!;
    expect(saved.creation).toEqual({ caseId: 'selected-case' });
    expect(saved.creationPending).toBe(true);
    expect(() => newBrowserSession('another-case')).toThrow();
    expect(loadBrowserSession()).toEqual(saved);
  });
  it('preserves a pending mutation when another case is selected', () => {
    const saved = { version: 1, sessionId: 'session-one', creationPending: false, pending: { resultSequence: null,
      command: { kind: 'investigate', sessionId: 'session-one', requestId: 'original-request', expectedSequence: 2, point: 'point_clock', route: 'route_clock' } } };
    values.set(browserSessionKey, JSON.stringify(saved));
    expect(() => newBrowserSession('another-case')).toThrow();
    expect(loadBrowserSession()).toEqual(saved);
  });
  it('removes discarded old locators without restoring them or changing the identity cookie', () => {
    for (let number = 1; number <= 6; number++) values.set(`detective-scenic:server-session:v${number}`, 'old locator');
    values.set('unrelated-user-preference', 'keep');
    expect(loadBrowserSession()).toBeNull();
    expect([...values.keys()]).toEqual(['unrelated-user-preference']);
  });
  it('retains a current locator while clearing old backups', () => {
    const current = { version: 1, sessionId: 'session-one', creationPending: false, pending: null };
    values.set(browserSessionKey, JSON.stringify(current));
    values.set('detective-scenic:server-session:v6', 'discarded');
    expect(loadBrowserSession()).toEqual(current);
    expect(values.has('detective-scenic:server-session:v6')).toBe(false);
  });
  it('does not hide corruption in the new locator by creating a replacement investigation', () => {
    values.set(browserSessionKey, 'invalid-json');
    expect(() => loadBrowserSession()).toThrow();
    expect(values.get(browserSessionKey)).toBe('invalid-json');
  });
});
