import { describe, expect, it } from 'vitest';
import { investigationSnapshotSchema } from './InvestigationSnapshot';
const material = { id: 'known', title: '已得材料', text: '原始观察', cited: false };
const base = { sessionId: 'session', caseId: 'study', caseRevision: { value: 20 }, sequence: { value: 1 }, title: '测试',
  scene: { bundleId: 'study', revision: '5', placeName: '书房', drawerOpened: false, windowOpened: false, objects: [] },
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
