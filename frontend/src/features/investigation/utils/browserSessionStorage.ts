import { browserSessionSchema, type BrowserSession } from '../objects/BrowserSession';
export const browserSessionKey = 'detective-scenic:server-session:v6';
const key = browserSessionKey;
export function parseBrowserSessionSnapshot(raw: string) {
  if (!raw) return null;
  try { return browserSessionSchema.safeParse(JSON.parse(raw)); }
  catch { return browserSessionSchema.safeParse(null); }
}
export function browserSessionSnapshot(): string {
  try { return localStorage.getItem(key) ?? localStorage.getItem('detective-scenic:server-session:v5') ?? ''; }
  catch { return '!unreadable'; }
}
export function subscribeBrowserSession(notify: () => void) {
  window.addEventListener('investigation-session-changed', notify);
  window.addEventListener('storage', notify);
  return () => { window.removeEventListener('investigation-session-changed', notify); window.removeEventListener('storage', notify); };
}
export function loadBrowserSession(): BrowserSession | null {
  const raw = localStorage.getItem(key);
  if (raw) return browserSessionSchema.parse(JSON.parse(raw));
  // Copy the previous locator, including an unresolved payload; retain the source as backup.
  const previous = localStorage.getItem('detective-scenic:server-session:v5');
  if (!previous) return null;
  const saved = browserSessionSchema.parse(JSON.parse(previous));
  saveBrowserSession(saved);
  return saved;
}
export function saveBrowserSession(session: BrowserSession): void {
  localStorage.setItem(key, JSON.stringify(browserSessionSchema.parse(session)));
  if (typeof window !== 'undefined') window.dispatchEvent(new Event('investigation-session-changed'));
}
