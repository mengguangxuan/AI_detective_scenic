import { describe, expect, it } from 'vitest';
import { estimateRoute } from './estimateRoute';
import type { AreaMap } from '../../../apis/games/objects/AreaMap';
const map: AreaMap = { note: '示意', locations: ['a', 'b', 'c', 'isolated'].map((id, i) => ({ id, name: id, description: '', x: 40 + i * 100, y: 50 })), roads: [
  { id: 'direct', from: 'a', to: 'c', meters: 900, label: '村道' },
  { id: 'one', from: 'a', to: 'b', meters: 150, label: '小巷' },
  { id: 'two', from: 'b', to: 'c', meters: 75, label: '路口' },
] };
describe('public map route estimates', () => {
  it('uses connected roads rather than the screen coordinates and supports both directions', () => {
    expect(estimateRoute(map, 'a', 'c')).toMatchObject({ meters: 225, minutes: 3 });
    expect(estimateRoute(map, 'c', 'a')?.roads.map(road => road.id)).toEqual(['two', 'one']);
    expect(map.roads.map(road => road.id)).toEqual(['direct', 'one', 'two']);
  });
  it('does not invent a route for disconnected or unknown places', () => {
    expect(estimateRoute(map, 'a', 'isolated')).toBeNull();
    expect(estimateRoute(map, 'missing', 'a')).toBeNull();
    expect(estimateRoute(map, 'a', 'a')).toMatchObject({ meters: 0, minutes: 0, roads: [] });
  });
});
