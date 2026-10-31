import type { InvestigationSnapshot } from '../../apis/games/objects/InvestigationSnapshot';

const outcomes = {
  Solved: {
    label: '推理已结案', heading: '本次推理已结案',
    description: '结案时的推理和复盘已保存。你仍可继续调查，原结案结果与材料对照会保留。',
    linkLabel: '查看结案复盘',
  },
  Revealed: {
    label: '已查看解答', heading: '已查看解答 · 本次未计为破案',
    description: '你已主动结束本次推理并查看作者解答。仍可继续调查，已有记录和原复盘会保留。',
    linkLabel: '回看作者解答',
  },
  Established: {
    label: '推理已成立，待结案', heading: '核心推理已成立 · 待确认结案',
    description: '你可以继续调查，或前往推理与复盘确认结案；确认前不会公开完整解答。',
    linkLabel: '前往结案选择',
  },
  Legacy: {
    label: '历史推理已提交', heading: '历史推理已提交 · 可回看复盘',
    description: '这份旧调查的原提交与复盘已保存，可继续调查。分数用于人物选择和材料引用覆盖，不表示文字推理已获评审通过。',
    linkLabel: '回看历史提交与复盘',
  },
} as const;

// Display only authoritative outcomes; local drafts and first-stage completion cannot end a case.
export function investigationOutcome(snapshot: Pick<InvestigationSnapshot, 'reasoning' | 'conclusion'>) {
  const ending = snapshot.reasoning?.ending;
  if (ending) return outcomes[ending.mode];
  if (snapshot.reasoning?.attempts.at(-1)?.status === 'Established') return outcomes.Established;
  if (snapshot.conclusion) return outcomes.Legacy;
  return null;
}
