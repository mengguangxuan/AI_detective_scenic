import { useEffect, useRef, useState } from 'react';
import { APIError } from '../../../apiClient/APIError';
import { ExecuteOperationAPI } from '../../../apis/games/ExecuteOperationAPI';
import { InvestigateAPI } from '../../../apis/games/InvestigateAPI';
import { GetInvestigationSnapshotAPI } from '../../../apis/games/GetInvestigationSnapshotAPI';
import type { PointId } from '../../../objects/PointId';
import type { OperationId } from '../../../objects/OperationId';
import type { RouteId } from '../../../objects/RouteId';
import { requestIdSchema } from '../../../objects/RequestId';
import type { InvestigationPageState } from '../objects/InvestigationPageState';
import type { PendingCommand } from '../objects/PendingCommand';
import type { BrowserSession } from '../objects/BrowserSession';
import { acceptSnapshot } from '../functions/acceptSnapshot';
import { connectInvestigation, newBrowserSession } from '../utils/investigationConnection';
import { saveBrowserSession } from '../utils/browserSessionStorage';

function problem(error: unknown): string {
  return error instanceof APIError ? error.message : '无法读取或保存会话，请检查连接和浏览器存储权限。';
}
export function useInvestigation() {
  const [state, setState] = useState<InvestigationPageState>({ kind: 'loading' });
  const stored = useRef<BrowserSession | null>(null);
  const inFlight = useRef(false);
  function remember(value: BrowserSession) { saveBrowserSession(value); stored.current = value; }
  function connected(result: Awaited<ReturnType<typeof connectInvestigation>>) {
    stored.current = result.stored;
    setState({ kind: 'ready', snapshot: result.snapshot, selected: null,
      notice: '已从后端读取当前调查进度。', mutation: result.stored.pending
        ? { kind: 'outcomeUnknown', pending: result.stored.pending.command, problem: '上次请求的结果尚未确认。请用原请求重试。' }
        : { kind: 'idle' } });
  }
  useEffect(() => {
    let active = true;
    void connectInvestigation().then(result => { if (active) connected(result); })
      .catch(error => { if (active) setState({ kind: 'failed', message: problem(error) }); });
    return () => { active = false; };
  }, []);
  async function reconnect() {
    if (inFlight.current) return;
    inFlight.current = true;
    setState({ kind: 'loading' });
    try { connected(await connectInvestigation()); }
    catch (error) { setState({ kind: 'failed', message: problem(error) }); }
    finally { inFlight.current = false; }
  }
  function select(point: PointId) {
    if (state.kind === 'ready' && state.snapshot.points.some(p => p.id === point))
      setState({ ...state, selected: point });
  }
  async function submit(command: PendingCommand) {
    if (inFlight.current || state.kind !== 'ready' || !stored.current) return;
    inFlight.current = true;
    const previous = state;
    try {
      // Save before sending: a reload must retain the exact request ID and payload.
      remember({ ...stored.current, pending: { command, resultSequence: null } });
    } catch {
      inFlight.current = false;
      setState({ ...previous, notice: '浏览器无法保存待处理请求，本次没有提交。' });
      return;
    }
    setState({ ...previous, mutation: { kind: 'submitting' }, notice: '正在提交调查…' });
    try {
      const result = command.kind === 'execute'
        ? await new ExecuteOperationAPI(command.sessionId, command.requestId, command.expectedSequence, command.operation).send()
        : await new InvestigateAPI(command.sessionId, command.requestId, command.expectedSequence, command.point, command.route).send();
      remember({ ...stored.current, pending: { command, resultSequence: result.sequence } });
      setState({ ...previous, mutation: { kind: 'awaitingSnapshot', problem: '操作已保存，正在刷新当前视图…' } });
      try {
        const next = await new GetInvestigationSnapshotAPI(command.sessionId).send();
        if (next.sequence < result.sequence) throw new Error('视图尚未更新');
        const snapshot = acceptSnapshot(previous.snapshot, next);
        remember({ ...stored.current, pending: null });
        setState({ kind: 'ready', snapshot, selected: snapshot.points.some(p => p.id === previous.selected) ? previous.selected : null,
          mutation: { kind: 'idle' }, notice: '调查结果已保存，当前视图已更新。' });
      } catch (error) {
        setState({ ...previous, mutation: { kind: 'awaitingSnapshot', problem: `操作已经保存。${problem(error)} 请重新读取视图。` } });
      }
    } catch (error) {
      if (error instanceof APIError && error.status !== null && error.status >= 400 && error.status < 500) {
        try {
          remember({ ...stored.current, pending: null });
          const next = await new GetInvestigationSnapshotAPI(command.sessionId).send();
          const snapshot = acceptSnapshot(previous.snapshot, next);
          setState({ ...previous, snapshot, selected: snapshot.points.some(p => p.id === previous.selected) ? previous.selected : null,
            mutation: { kind: 'idle' }, notice: error.message });
        } catch {
          setState({ ...previous, mutation: { kind: 'awaitingSnapshot', problem: `${error.message} 请重新连接并读取当前视图。` } });
        }
      } else {
        setState({ ...previous, mutation: { kind: 'outcomeUnknown', pending: command, problem: problem(error) } });
      }
    } finally { inFlight.current = false; }
  }
  function execute(operation: OperationId) {
    if (state.kind !== 'ready' || state.mutation.kind !== 'idle') return;
    const point = state.snapshot.points.find(p => p.id === state.selected);
    if (!point?.operations.some(o => o.id === operation)) return;
    void submit({ kind: 'execute', sessionId: state.snapshot.sessionId, requestId: requestIdSchema.parse(crypto.randomUUID()),
      expectedSequence: state.snapshot.sequence, operation });
  }
  function investigate(route: RouteId) {
    if (state.kind !== 'ready' || state.mutation.kind !== 'idle') return;
    const point = state.snapshot.points.find(p => p.id === state.selected);
    if (!point?.routes.some(r => r.id === route)) return;
    void submit({ kind: 'investigate', sessionId: state.snapshot.sessionId, requestId: requestIdSchema.parse(crypto.randomUUID()),
      expectedSequence: state.snapshot.sequence, point: point.id, route });
  }
  function retry() { if (state.kind === 'ready' && state.mutation.kind === 'outcomeUnknown') void submit(state.mutation.pending); }
  function reset() {
    if (inFlight.current || (state.kind === 'ready' && state.mutation.kind !== 'idle')) return;
    if (!window.confirm('新建一次调查？原会话保留在服务器，当前页面将切换到新会话。')) return;
    try { newBrowserSession(); void reconnect(); }
    catch { setState({ kind: 'failed', message: '无法保存新会话编号；没有提交新建请求。' }); }
  }
  return { state, select, execute, investigate, reconnect, retry, reset };
}
