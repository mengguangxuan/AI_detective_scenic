import type { AnchorHTMLAttributes, MouseEvent } from 'react';

type Props = AnchorHTMLAttributes<HTMLAnchorElement> & { onNavigate?: () => void };
export default function InvestigationLink({ onNavigate, onClick, ...props }: Props) {
  function follow(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event);
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey ||
      event.currentTarget.target === '_blank' || event.currentTarget.hasAttribute('download')) return;
    const destination = new URL(event.currentTarget.href);
    if (destination.origin !== window.location.origin) return;
    event.preventDefault();
    onNavigate?.();
    if (window.location.href === destination.href) return;
    window.history.pushState(null, '', destination.pathname + destination.search + destination.hash);
    window.dispatchEvent(new PopStateEvent('popstate'));
    window.scrollTo(0, 0);
  }
  return <a {...props} onClick={follow}/>;
}
