import { browserSessionSchema, type BrowserSession } from '../objects/BrowserSession';
export const browserSessionKey = 'detective-scenic:investigation-session';
const key = browserSessionKey;
// Explicit one-time removal authorized for the discarded case-history model.
function clearObsoleteLocators(): void {
  for (let number = 1; number <= 6; number++) localStorage.removeItem(`detective-scenic:server-session:v${number}`);
}
export function parseBrowserSessionSnapshot(raw: string) {
  if (!raw) return null;
  try { return browserSessionSchema.safeParse(JSON.parse(raw)); }
  catch { return browserSessionSchema.safeParse(null); }
}
export function browserSessionSnapshot(): string {
  try { clearObsoleteLocators(); return localStorage.getItem(key) ?? ''; }
  catch { return '!unreadable'; }
}
export function subscribeBrowserSession(notify: () => void) {
  window.addEventListener('investigation-session-changed', notify);
  window.addEventListener('storage', notify);
  return () => { window.removeEventListener('investigation-session-changed', notify); window.removeEventListener('storage', notify); };
}
export function loadBrowserSession(): BrowserSession | null {
  clearObsoleteLocators();
  const raw = localStorage.getItem(key);
  return raw ? browserSessionSchema.parse(JSON.parse(raw)) : null;
}
export function saveBrowserSession(session: BrowserSession): void {
  localStorage.setItem(key, JSON.stringify(browserSessionSchema.parse(session)));
  if (typeof window !== 'undefined') window.dispatchEvent(new Event('investigation-session-changed'));
}
