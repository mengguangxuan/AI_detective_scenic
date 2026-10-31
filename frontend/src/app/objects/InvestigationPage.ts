// Supported UI destinations; case content and protocol are separate.
export const investigationPages = [
  { path: '/case', label: '案件简介' },
  { path: '/map', label: '地点地图' },
  { path: '/scene', label: '现场探索' },
  { path: '/clues', label: '线索整理' },
  { path: '/people', label: '人物档案' },
  { path: '/interviews', label: '人物问询' },
  { path: '/police', label: '警方交流' },
  { path: '/conclusion', label: '推理与复盘' },
  { path: '/journal', label: '调查日志' },
] as const;
export type InvestigationPage = typeof investigationPages[number]['path'];
export function resolveInvestigationPage(pathname: string): InvestigationPage | null {
  const path = pathname.replace(/\/+$/, '') || '/';
  if (path === '/') return '/scene';
  return investigationPages.find(page => page.path === path)?.path ?? null;
}
