import { describe, expect, it } from 'vitest';
import { layoutRoadLabels } from './layoutRoadLabels';
import type { AreaMap } from '../../../apis/games/objects/AreaMap';
const map: AreaMap = { note: '测试布局', locations: [
  { id: 'gate', name: '入口', description: '', x: 100, y: 300 },
  { id: 'gallery', name: '长廊', description: '', x: 900, y: 300 },
  { id: 'room', name: '房间', description: '', x: 500, y: 300 },
], roads: [{ id: 'farm_path', from: 'gate', to: 'gallery', meters: 200, label: '道路' }] };
describe('authored map label placement', () => {
  it('avoids location boxes and has no story-specific road-ID offsets', () => {
    const label = layoutRoadLabels(map).farm_path!;
    expect(map.locations.some(location => Math.abs(label.x - location.x) < 124 && Math.abs(label.y - location.y) < 38)).toBe(false);
    const renamed = { ...map, roads: map.roads.map(road => ({ ...road, id: 'unrelated_road' })) };
    expect(layoutRoadLabels(renamed).unrelated_road).toEqual(label);
    expect(map.roads[0]?.id).toBe('farm_path');
  });
  it('keeps finite labels inside the supported viewport even for overlapping location coordinates', () => {
    const overlapping = { ...map, locations: map.locations.map(location => ({ ...location, x: 40, y: 40 })) };
    const label = layoutRoadLabels(overlapping).farm_path!;
    expect(Number.isFinite(label.x) && Number.isFinite(label.y)).toBe(true);
    expect(label.x).toBeGreaterThanOrEqual(70); expect(label.x).toBeLessThanOrEqual(1130);
    expect(label.y).toBeGreaterThanOrEqual(20); expect(label.y).toBeLessThanOrEqual(680);
  });
});
