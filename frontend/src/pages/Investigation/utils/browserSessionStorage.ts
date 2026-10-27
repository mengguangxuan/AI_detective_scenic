import { browserSessionSchema, type BrowserSession } from '../objects/BrowserSession';
const key = 'detective-scenic:server-session:v2';
export function loadBrowserSession(): BrowserSession | null {
  // The user explicitly discarded the previous demo; do not keep parallel browser saves.
  localStorage.removeItem('detective-scenic:server-session:v1');
  localStorage.removeItem('detective-scenic:preview:v1');
  const raw = localStorage.getItem(key);
  if (!raw) return null;
  return browserSessionSchema.parse(JSON.parse(raw));
}
export function saveBrowserSession(session: BrowserSession): void {
  localStorage.setItem(key, JSON.stringify(session));
}
