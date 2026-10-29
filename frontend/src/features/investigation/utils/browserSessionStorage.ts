import { browserSessionSchema, type BrowserSession } from '../objects/BrowserSession';
const key = 'detective-scenic:server-session:v6';
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
  localStorage.setItem(key, JSON.stringify(session));
}
