import { browserSessionSchema, type BrowserSession } from '../objects/BrowserSession';
const key = 'detective-scenic:server-session:v1';
export function loadBrowserSession(): BrowserSession | null {
  const raw = localStorage.getItem(key);
  if (!raw) return null;
  return browserSessionSchema.parse(JSON.parse(raw));
}
export function saveBrowserSession(session: BrowserSession): void {
  localStorage.setItem(key, JSON.stringify(session));
}
