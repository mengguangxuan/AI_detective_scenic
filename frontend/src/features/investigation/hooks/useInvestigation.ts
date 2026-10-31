import { EvaluateReasoningAPI } from '../../../apis/games/EvaluateReasoningAPI';
import { RevealReasoningAPI } from '../../../apis/games/RevealReasoningAPI';
import { SubmitConclusionAPI } from '../../../apis/games/SubmitConclusionAPI';
import type { PersonId } from '../../../objects/PersonId';
import type { ExternalCheckId } from '../../../objects/ExternalCheckId';
import { RequestExternalCheckAPI } from '../../../apis/games/RequestExternalCheckAPI';
import { AdvanceTimeAPI } from '../../../apis/games/AdvanceTimeAPI';
import type { WitnessId } from '../../../objects/WitnessId';
import { useEffect, useRef, useState } from 'react';
import { APIError } from '../../../apiClient/APIError';
import { ExecuteOperationAPI } from '../../../apis/games/ExecuteOperationAPI';
import { InvestigateAPI } from '../../../apis/games/InvestigateAPI';
import { AskWitnessAPI } from '../../../apis/games/AskWitnessAPI';
import { ReportCluesAPI } from '../../../apis/games/ReportCluesAPI';
import { CompareTimesAPI } from '../../../apis/games/CompareTimesAPI';
import type { TimeAssessment } from '../../../apis/games/objects/TimeAssessment';
import type { SceneObjectBinding } from '../../../apis/games/objects/SceneObjectBinding';
import type { ClueId } from '../../../objects/ClueId';
import type { SceneSelection } from '../objects/SceneSelection';
import { GetInvestigationSnapshotAPI } from '../../../apis/games/GetInvestigationSnapshotAPI';
import type { SceneNodeId } from '../../../objects/SceneNodeId';
import { retainSelection } from '../functions/retainSelection';
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
  const [lastReply, setLastReply] = useState<{ point: PointId; text: string } | null>(null);
  const stored = useRef<BrowserSession | null>(null);
  const inFlight = useRef(false);
  function remember(value: BrowserSession) { saveBrowserSession(value); stored.current = value; }
  function connected(result: Awaited<ReturnType<typeof connectInvestigation>>) {
    setLastReply(null);
    stored.current = result.stored;
    setState({ kind: 'ready', snapshot: result.snapshot, selected: { kind: 'none' },
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
      setState({ ...state, selected: { kind: 'point', id: point } });
  }
  function observe(node: SceneNodeId) {
    if (state.kind === 'ready' && state.snapshot.observations.some(item => item.node === node))
      setState({ ...state, selected: { kind: 'observation', node } });
  }
  function overview() {
    if (state.kind === 'ready') setState({ ...state, selected: { kind: 'none' } });
  }
  async function submit(command: PendingCommand, selection?: SceneSelection) {
    if (inFlight.current || state.kind !== 'ready' || !stored.current) return;
    inFlight.current = true;
    const previous = { ...state, selected: selection ?? state.selected };
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
      const result = await (async () => {
        switch (command.kind) {
          case 'evaluate': return new EvaluateReasoningAPI(command.sessionId, command.requestId, command.expectedSequence, command.suspects, command.evidence, command.explanation).send();
          case 'revealReasoning': return new RevealReasoningAPI(command.sessionId, command.requestId, command.expectedSequence, command.abandon).send();
          case 'conclude': return new SubmitConclusionAPI(command.sessionId, command.requestId, command.expectedSequence, command.suspects, command.evidence, command.explanation).send();
          case 'check': return new RequestExternalCheckAPI(command.sessionId, command.requestId, command.expectedSequence, command.check).send();
          case 'wait': return new AdvanceTimeAPI(command.sessionId, command.requestId, command.expectedSequence, command.minutes).send();
          case 'execute': return new ExecuteOperationAPI(command.sessionId, command.requestId, command.expectedSequence, command.operation).send();
          case 'investigate': return new InvestigateAPI(command.sessionId, command.requestId, command.expectedSequence, command.point, command.route).send();
          case 'ask': return new AskWitnessAPI(command.sessionId, command.requestId, command.expectedSequence, command.witness, command.question, command.evidence).send();
          case 'report': return new ReportCluesAPI(command.sessionId, command.requestId, command.expectedSequence, command.evidence).send();
          case 'compare': return new CompareTimesAPI(command.sessionId, command.requestId, command.expectedSequence, command.evidence, command.convertedTimeMinutes, command.assessment).send();
        }
      })();
      remember({ ...stored.current, pending: { command, resultSequence: result.sequence } });
      setState({ ...previous, mutation: { kind: 'awaitingSnapshot', problem: '操作已保存，正在刷新当前视图…' } });
      try {
        const next = await new GetInvestigationSnapshotAPI(command.sessionId).send();
        if (next.sequence < result.sequence) throw new Error('视图尚未更新');
        const snapshot = acceptSnapshot(previous.snapshot, next);
        remember({ ...stored.current, pending: null });
        if (command.kind === 'investigate') setLastReply({ point: command.point, text: result.findings.join('\n') });
        setState({ kind: 'ready', snapshot, selected: retainSelection(previous.selected,snapshot),
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
          setState({ ...previous, snapshot, selected: retainSelection(previous.selected,snapshot),
            mutation: { kind: 'idle' }, notice: error.message });
        } catch {
          setState({ ...previous, mutation: { kind: 'awaitingSnapshot', problem: `${error.message} 请重新连接并读取当前视图。` } });
        }
      } else {
        setState({ ...previous, mutation: { kind: 'outcomeUnknown', pending: command, problem: problem(error) } });
      }
    } finally { inFlight.current = false; }
  }
  function execute(operation: OperationId, target?: PointId) {
    if (state.kind !== 'ready' || state.mutation.kind !== 'idle') return;
    const point = state.snapshot.points.find(p => target ? p.id === target : state.selected.kind === 'point' && p.id === state.selected.id);
    if (!point?.operations.some(o => o.id === operation)) return;
    void submit({ kind: 'execute', sessionId: state.snapshot.sessionId, requestId: requestIdSchema.parse(crypto.randomUUID()),
      expectedSequence: state.snapshot.sequence, operation }, { kind: 'point', id: point.id });
  }
  function investigate(route: RouteId, target?: PointId) {
    if (state.kind !== 'ready' || state.mutation.kind !== 'idle') return;
    const point = state.snapshot.points.find(p => target ? p.id === target : state.selected.kind === 'point' && p.id === state.selected.id);
    if (!point?.routes.some(r => r.id === route)) return;
    void submit({ kind: 'investigate', sessionId: state.snapshot.sessionId, requestId: requestIdSchema.parse(crypto.randomUUID()),
      expectedSequence: state.snapshot.sequence, point: point.id, route }, { kind: 'point', id: point.id });
  }
  function retry() { if (state.kind === 'ready' && state.mutation.kind === 'outcomeUnknown') void submit(state.mutation.pending); }
  function inspect(object: SceneObjectBinding) {
    if (state.kind !== 'ready') return;
    const registered = state.snapshot.scene.objects.find(o => o.node === object.node);
    if (!registered) return;
    const selection: SceneSelection = registered.point
      ? { kind: 'point', id: registered.point } : { kind: 'observation', node: registered.node };
    setState({ ...state, selected: selection });
    const point = state.snapshot.points.find(p => p.id === registered.point);
    const route = point?.routes.find(r => r.id === registered.readRoute);
    if (point && route && state.mutation.kind === 'idle') void submit({ kind: 'investigate', sessionId: state.snapshot.sessionId,
      requestId: requestIdSchema.parse(crypto.randomUUID()), expectedSequence: state.snapshot.sequence, point: point.id, route: route.id }, selection);
  }
  function compare(evidence: readonly ClueId[], convertedTimeMinutes: number, assessment: TimeAssessment) {
    if (state.kind !== 'ready' || state.mutation.kind !== 'idle') return;
    void submit({ kind: 'compare', sessionId: state.snapshot.sessionId, requestId: requestIdSchema.parse(crypto.randomUUID()),
      expectedSequence: state.snapshot.sequence, evidence: [...evidence], convertedTimeMinutes, assessment });
  }
  function ask(witness: WitnessId, question: string, evidence: readonly ClueId[]) {
    if (state.kind !== 'ready' || state.mutation.kind !== 'idle' || !question.trim() || question.length > 1000) return;
    const profile = state.snapshot.witnesses.find(w => w.id === witness);
    if (!profile || evidence.some(id => !state.snapshot.clues.some(c => c.id === id))) return;
    void submit({ kind: 'ask', sessionId: state.snapshot.sessionId, requestId: requestIdSchema.parse(crypto.randomUUID()),
      expectedSequence: state.snapshot.sequence, witness, question: question.trim(), evidence: [...evidence] }, { kind: 'point', id: profile.point });
  }
  function report(evidence: readonly ClueId[]) {
    if (state.kind !== 'ready' || state.mutation.kind !== 'idle' || !state.snapshot.police || evidence.length < 1 || evidence.length > 20 ||
      evidence.some(id => !state.snapshot.clues.some(clue => clue.id === id))) return;
    void submit({ kind: 'report', sessionId: state.snapshot.sessionId, requestId: requestIdSchema.parse(crypto.randomUUID()),
      expectedSequence: state.snapshot.sequence, evidence: [...evidence] });
  }
  function requestCheck(check: ExternalCheckId) {
    if (state.kind !== 'ready' || state.mutation.kind !== 'idle' || !state.snapshot.externalChecks.some(item => item.id === check && item.available)) return;
    void submit({ kind: 'check', sessionId: state.snapshot.sessionId, requestId: requestIdSchema.parse(crypto.randomUUID()), expectedSequence: state.snapshot.sequence, check });
  }
  function advanceTime(minutes: number) {
    if (state.kind !== 'ready' || state.mutation.kind !== 'idle' || !state.snapshot.clock || !Number.isInteger(minutes) || minutes < 1 || minutes > 30) return;
    void submit({ kind: 'wait', sessionId: state.snapshot.sessionId, requestId: requestIdSchema.parse(crypto.randomUUID()), expectedSequence: state.snapshot.sequence, minutes });
  }
  function conclude(suspects: readonly PersonId[], evidence: readonly ClueId[], explanation: string) {
    if (state.kind !== 'ready' || state.mutation.kind !== 'idle' || state.snapshot.conclusion ||
      !suspects.length || suspects.some(id => !state.snapshot.conclusionCandidates.includes(id)) || !evidence.length || evidence.length > 50 ||
      evidence.some(id => !state.snapshot.clues.some(c => c.id === id)) || explanation.trim().length < 10 || explanation.length > 1000) return;
    void submit({ kind: 'conclude', sessionId: state.snapshot.sessionId, requestId: requestIdSchema.parse(crypto.randomUUID()),
      expectedSequence: state.snapshot.sequence, suspects: [...suspects], evidence: [...evidence], explanation });
  }
  function evaluate(suspects: readonly PersonId[], evidence: readonly ClueId[], explanation: string) {
    if (state.kind !== 'ready' || state.mutation.kind !== 'idle' || !state.snapshot.reasoning || state.snapshot.reasoning.ending ||
      state.snapshot.reasoning.attempts.at(-1)?.status === 'Established' || !suspects.length ||
      suspects.some(id => !state.snapshot.conclusionCandidates.includes(id)) || !evidence.length || evidence.length > 50 ||
      evidence.some(id => !state.snapshot.clues.some(c => c.id === id)) || explanation.trim().length < 10 || explanation.length > 1000) return;
    void submit({ kind: 'evaluate', sessionId: state.snapshot.sessionId, requestId: requestIdSchema.parse(crypto.randomUUID()),
      expectedSequence: state.snapshot.sequence, suspects: [...suspects], evidence: [...evidence], explanation });
  }
  function revealReasoning(abandon: boolean) {
    if (state.kind !== 'ready' || state.mutation.kind !== 'idle' || !state.snapshot.reasoning || state.snapshot.reasoning.ending ||
      (!abandon && state.snapshot.reasoning.attempts.at(-1)?.status !== 'Established')) return;
    void submit({ kind: 'revealReasoning', sessionId: state.snapshot.sessionId, requestId: requestIdSchema.parse(crypto.randomUUID()),
      expectedSequence: state.snapshot.sequence, abandon });
  }
  function reset() {
    if (inFlight.current || (state.kind === 'ready' && state.mutation.kind !== 'idle')) return;
    if (!window.confirm('新建一次调查？原会话保留在服务器，当前页面将切换到新会话。')) return;
    try { newBrowserSession(); void reconnect(); }
    catch { setState({ kind: 'failed', message: '无法保存新会话编号；没有提交新建请求。' }); }
  }
  return { state, evaluate, revealReasoning, conclude, requestCheck, advanceTime, lastReply, select, observe, overview, inspect, compare, ask, report, execute, investigate, reconnect, retry, reset };
}
