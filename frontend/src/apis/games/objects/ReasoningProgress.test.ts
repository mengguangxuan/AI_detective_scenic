import { describe, expect, it } from 'vitest';
import { investigationSnapshotSchema } from './InvestigationSnapshot';
const material = { id: 'known', title: '已得材料', text: '原始观察', cited: false };
const base = { sessionId: 'session', caseId: 'study', sequence: { value: 1 }, title: '测试',
  scene: { bundleId: 'study', placeName: '书房', openedEntities: [], objects: [] },
  points: [], clues: [{ id: material.id, title: material.title, text: material.text }], journal: [], observations: [], witnesses: [], interviews: [], police: null, people: [], stage: null };
function snapshot(m = material) {
  return { ...base, reasoning: { attempts: [], ending: { sequence: { value: 1 }, mode: 'Revealed', review: '作者说明',
    sections: [{ title: '主题', explanation: '作者解释', boundary: '不构成证明', materials: [m] }] } } };
}
describe('frozen review materials', () => {
  it('accepts acquired observations but rejects unearned IDs, rewritten text and fabricated citations', () => {
    expect(investigationSnapshotSchema.safeParse(snapshot()).success).toBe(true);
    for (const changed of [{ ...material, id: 'unearned' }, { ...material, text: '事后补造的观察' }, { ...material, cited: true }])
      expect(investigationSnapshotSchema.safeParse(snapshot(changed)).success).toBe(false);
  });
  it('restores old endings without requiring review sections', () => {
    const parsed = investigationSnapshotSchema.parse({ ...base, reasoning: { attempts: [], ending: { sequence: { value: 1 }, mode: 'Revealed', review: '历史原文' } } });
    expect(parsed.reasoning!.ending!.sections).toEqual([]);
    expect(parsed.reasoning!.ending!.review).toBe('历史原文');
  });
});

describe('case-specific coverage groups', () => {
  it('accepts proportional scoring for two, three and seven groups, and rejects fixed ten-point scoring', () => {
    for (const count of [2, 3, 7]) {
      const conclusion = { sequence: { value: 1 }, suspects: ['suspect'], evidence: ['known'],
        explanation: '这是一段用于核对材料覆盖的推理原文', suspectScore: 50, evidenceScore: Math.floor(50 / count),
        coverage: Array.from({ length: count }, (_, index) => ({ label: `group${index}`, requiredClues: [index === 0 ? 'known' : `missing${index}`], covered: index === 0 })), review: '作者复盘' };
      const data = { ...base, conclusionCandidates: ['suspect'], people: [{ id: 'suspect', name: '嫌疑人', identity: '人物', victimRelation: '认识死者', caseRelation: '公开简介', witness: null }], conclusion };
      expect(investigationSnapshotSchema.safeParse(data).success).toBe(true);
      expect(investigationSnapshotSchema.safeParse({ ...data, conclusion: { ...conclusion, evidenceScore: 10 } }).success).toBe(false);
    }
  });
});

describe('visible scene entity states', () => {
  it('accepts authored entity IDs and rejects hidden or duplicate open-state entries', () => {
    const object = { node: 'node_front', entity: 'cabinet_alpha', label: '柜体', point: null, readRoute: null };
    const data = { ...base, scene: { ...base.scene, objects: [object], openedEntities: ['cabinet_alpha'] },
      observations: [{ node: object.node, label: '柜体', description: '公开外观' }] };
    expect(investigationSnapshotSchema.safeParse(data).success).toBe(true);
    for (const openedEntities of [['hidden_object'], ['cabinet_alpha', 'cabinet_alpha']])
      expect(investigationSnapshotSchema.safeParse({ ...data, scene: { ...data.scene, openedEntities } }).success).toBe(false);
  });
});
