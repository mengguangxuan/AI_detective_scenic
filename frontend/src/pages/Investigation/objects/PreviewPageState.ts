import type { PreviewSession } from './PreviewSession';
import type { PreviewPointId } from './PreviewPointId';
export type PreviewPageState = Readonly<{
  session: PreviewSession;
  selected: PreviewPointId | null;
  notice: string;
  storageProblem: string | null;
}>;
