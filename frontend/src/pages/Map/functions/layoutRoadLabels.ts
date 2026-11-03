import type { AreaMap } from '../../../apis/games/objects/AreaMap';
type Position = { x: number; y: number };
// Place distance labels near their roads while avoiding authored location boxes and earlier labels.
export function layoutRoadLabels(map: AreaMap): Record<string, Position> {
  const locations = new Map(map.locations.map(location => [location.id, location]));
  const result: Record<string, Position> = {};
  for (const road of map.roads) {
    const from = locations.get(road.from), to = locations.get(road.to);
    if (!from || !to) continue;
    const dx = to.x - from.x, dy = to.y - from.y, length = Math.hypot(dx, dy);
    const normal = length ? { x: -dy / length, y: dx / length } : { x: 0, y: -1 };
    let best: Position | null = null, bestCost = Infinity;
    for (const fraction of [0.5, 0.35, 0.65]) for (const offset of [-24, 24, -46, 46, -68, 68]) {
      const candidate = { x: Math.max(70, Math.min(1130, from.x + dx * fraction + normal.x * offset)),
        y: Math.max(20, Math.min(680, from.y + dy * fraction + normal.y * offset)) };
      const conflicts = map.locations.filter(location => Math.abs(candidate.x - location.x) < 124 && Math.abs(candidate.y - location.y) < 38).length +
        Object.values(result).filter(label => Math.abs(candidate.x - label.x) < 100 && Math.abs(candidate.y - label.y) < 24).length;
      const cost = conflicts * 10000 + Math.abs(offset) + Math.abs(fraction - 0.5) * 100;
      if (cost < bestCost) { best = candidate; bestCost = cost; }
    }
    if (best) result[road.id] = best;
  }
  return result;
}
