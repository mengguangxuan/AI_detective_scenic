import { useSyncExternalStore } from 'react';
function subscribe(notify: () => void) {
  window.addEventListener('popstate', notify);
  return () => window.removeEventListener('popstate', notify);
}
export function usePathname() { return useSyncExternalStore(subscribe, () => window.location.pathname); }
export function navigate(path: string) {
  window.history.pushState(null, '', path);
  window.dispatchEvent(new PopStateEvent('popstate'));
  window.scrollTo(0, 0);
}
