import { z } from 'zod';
export const areaMapSchema = z.object({
  note: z.string().min(1),
  locations: z.array(z.object({ id: z.string().min(1), name: z.string().min(1), description: z.string(), x: z.number().min(40).max(1160), y: z.number().min(40).max(660) }).strict()).min(1),
  roads: z.array(z.object({ id: z.string().min(1), from: z.string(), to: z.string(), meters: z.number().int().positive().max(10000), label: z.string() }).strict()),
}).strict().superRefine((map, context) => {
  const ids = new Set(map.locations.map(location => location.id));
  if (ids.size !== map.locations.length || new Set(map.roads.map(road => road.id)).size !== map.roads.length || map.roads.some(road => road.from === road.to || !ids.has(road.from) || !ids.has(road.to)))
    context.addIssue({ code: 'custom', message: '地图地点或道路引用不正确' });
});
export type AreaMap = z.infer<typeof areaMapSchema>;
