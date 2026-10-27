import { useState } from 'react';
import type { PreviewPageState } from '../objects/PreviewPageState';
import type { InvestigationAction } from '../objects/InvestigationAction';
import type { PreviewPointId } from '../objects/PreviewPointId';
import { applyPreviewAction, initialPreview, visiblePoints } from '../functions/previewRules';
import { loadPreview, savePreview } from '../utils/previewStorage';

export function useInvestigationPreview() {
  const [state, setState] = useState<PreviewPageState>(() => {
    const saved = loadPreview();
    return { session: saved.session, selected: null, notice: saved.session.letterRead ? '已恢复预览进度：信件内容已记录。' : saved.session.drawerOpened ? '已恢复预览进度：抽屉已打开，可以检查信件。' : '从书桌的抽屉开始调查。', storageProblem: saved.problem };
  });
  function select(point: PreviewPointId) {
    if (visiblePoints(state.session).includes(point)) setState(s => ({ ...s, selected: point }));
  }
  function execute(action: InvestigationAction) {
    const session = applyPreviewAction(state.session, action);
    if (session === state.session) return;
    const problem = savePreview(session);
    setState(s => ({ ...s, session, storageProblem: problem,
      notice: action === 'open-drawer' ? '抽屉已打开。现在可以选择信件。' : '已将信件内容加入线索笔记。' }));
  }
  function reset() {
    if (!window.confirm('重新开始本地预览？当前预览进度将被替换。')) return;
    const problem = savePreview(initialPreview);
    setState({ session: initialPreview, selected: null, storageProblem: problem, notice: '已重新开始预览。' });
  }
  return { state, select, execute, reset };
}
