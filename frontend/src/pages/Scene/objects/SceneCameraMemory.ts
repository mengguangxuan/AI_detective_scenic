import type { SessionId } from '../../../objects/SessionId';
export type SceneCameraMemory = { sessionId: SessionId;
  sceneKey?: string; stopKey?: string;
  position: [number, number, number]; target: [number, number, number] };
