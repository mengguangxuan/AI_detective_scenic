import { useSyncExternalStore } from 'react';
import { resolveInvestigationPage } from '../objects/InvestigationPage';
function subscribe(notify: () => void) {
  window.addEventListener('popstate', notify);
  return () => window.removeEventListener('popstate', notify);
}
function pathname() { return window.location.pathname; }
export function useInvestigationPage() {
  return resolveInvestigationPage(useSyncExternalStore(subscribe, pathname));
}
