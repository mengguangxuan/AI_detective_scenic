import { z } from 'zod';
import { apiName } from './apiName';
import { APIError } from './APIError';
const errorSchema = z.object({ code: z.string(), publicMessage: z.string() }).strict();
export async function sendMessage<S extends z.ZodTypeAny>(className: string, input: unknown, schema: S): Promise<z.output<S>> {
  let response: Response;
  try {
    response = await fetch(`/api/${apiName(className)}`, { method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-Detective-Client': 'web' }, body: JSON.stringify(input), signal: AbortSignal.timeout(12000) });
  } catch { throw new APIError(null, 'NetworkFailure', '无法确认服务器结果，请检查后端连接。'); }
  let raw: unknown;
  try { raw = await response.json(); }
  catch { throw new APIError(null, 'InvalidResponse', '无法识别服务器响应，操作结果可能已经保存。'); }
  if (!response.ok) {
    const decoded = errorSchema.safeParse(raw);
    if (!decoded.success) throw new APIError(null, 'InvalidResponse', '服务器错误响应无法识别。');
    throw new APIError(response.status, decoded.data.code, decoded.data.publicMessage);
  }
  const decoded = schema.safeParse(raw);
  if (!decoded.success) throw new APIError(null, 'InvalidResponse', '服务器响应格式不匹配，请保留原请求。');
  return decoded.data;
}
