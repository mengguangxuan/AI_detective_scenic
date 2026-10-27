import { previewSessionSchema, type PreviewSession } from '../objects/PreviewSession';
import { initialPreview } from '../functions/previewRules';
const key = 'detective-scenic:preview:v1';
export function loadPreview(): { session: PreviewSession; problem: string | null } {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return { session: initialPreview, problem: null };
    const parsed = previewSessionSchema.safeParse(JSON.parse(raw));
    return parsed.success ? { session: parsed.data, problem: null }
      : { session: initialPreview, problem: '本地预览进度无法识别，已从初始状态开始。' };
  } catch {
    return { session: initialPreview, problem: '无法读取本地预览进度；本次仍可调查。' };
  }
}
export function savePreview(session: PreviewSession): string | null {
  try { localStorage.setItem(key, JSON.stringify(session)); return null; }
  catch { return '无法保存本地预览进度；刷新后可能丢失。'; }
}
