import type { PreviewSession } from '../objects/PreviewSession';
import type { InvestigationAction } from '../objects/InvestigationAction';
import type { PreviewPointId } from '../objects/PreviewPointId';

export const initialPreview: PreviewSession = { version: 1, sequence: 0, drawerOpened: false, letterRead: false };
export function visiblePoints(session: PreviewSession): readonly PreviewPointId[] {
  return session.drawerOpened ? ['point_drawer', 'point_letter'] : ['point_drawer'];
}
export function applyPreviewAction(session: PreviewSession, action: InvestigationAction): PreviewSession {
  if (action === 'open-drawer' && !session.drawerOpened)
    return { ...session, drawerOpened: true, sequence: session.sequence + 1 };
  if (action === 'read-letter' && session.drawerOpened && !session.letterRead)
    return { ...session, letterRead: true, sequence: session.sequence + 1 };
  return session;
}
export function previewJournal(session: PreviewSession): readonly string[] {
  return [ ...(session.drawerOpened ? ['打开书桌抽屉，发现一封折起的信。'] : []),
    ...(session.letterRead ? ['检查信件，记录信上的文字。'] : []) ];
}
