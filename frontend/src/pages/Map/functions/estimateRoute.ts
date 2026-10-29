import type { AreaMap } from '../../../apis/games/objects/AreaMap';
export function estimateRoute(map: AreaMap, from: string, to: string) {
  if (!map.locations.some(location => location.id === from) || !map.locations.some(location => location.id === to)) return null;
  const remaining = new Set(map.locations.map(location => location.id));
  const distance = new Map<string, number>([[from, 0]]);
  const previous = new Map<string, { place: string; road: AreaMap['roads'][number] }>();
  while (remaining.size > 0) {
    let current: string | undefined;
    for (const id of remaining) if (current === undefined || (distance.get(id) ?? Infinity) < (distance.get(current) ?? Infinity)) current = id;
    if (current === undefined || !Number.isFinite(distance.get(current) ?? Infinity)) break;
    remaining.delete(current);
    if (current === to) break;
    for (const road of map.roads) {
      const next = road.from === current ? road.to : road.to === current ? road.from : null;
      if (!next || !remaining.has(next)) continue;
      const candidate = distance.get(current)! + road.meters;
      if (candidate < (distance.get(next) ?? Infinity)) { distance.set(next, candidate); previous.set(next, { place: current, road }); }
    }
  }
  const meters = distance.get(to);
  if (meters === undefined) return null;
  const roads: AreaMap['roads'] = [];
  let current = to;
  while (current !== from) {
    const step = previous.get(current);
    if (!step) return null;
    roads.unshift(step.road); current = step.place;
  }
  return { meters, minutes: Math.ceil(meters / 75), roads };
}
