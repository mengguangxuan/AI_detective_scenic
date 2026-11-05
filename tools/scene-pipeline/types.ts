import type * as THREE from 'three';

export type Vec3 = Readonly<{ x: number; y: number; z: number }>;
export type EulerRadians = Readonly<{ xRad: number; yRad: number; zRad: number; order: 'XYZ' }>;
export type Transform = Readonly<{ positionMeters: Vec3; rotation: EulerRadians; scale: Vec3 }>;
export type JsonValue = null | boolean | number | string | readonly JsonValue[] | { readonly [key: string]: JsonValue };
export type SourceFieldRef = Readonly<{ documentId: string; pointer: string }>;
export type ModuleBuildInput = Readonly<{
  parameters: JsonValue;
  sizeBudget: { widthMeters: number; heightMeters: number; depthMeters: number };
}>;
export type BuiltModule = Readonly<{
  root: THREE.Group;
  nodes: Readonly<Record<string, THREE.Object3D>>;
  anchors: Readonly<Record<string, { nodeKey: string; pointMeters: Vec3 }>>;
}>;
export type SceneKit = Readonly<{
  materials: Readonly<Record<string, THREE.Material>>;
  box(parent: THREE.Object3D, size: [number, number, number], at: [number, number, number], material: THREE.Material): THREE.Mesh;
  cylinder(parent: THREE.Object3D, size: [number, number, number], at: [number, number, number], material: THREE.Material, rotation?: [number, number, number]): THREE.Mesh;
  tube(parent: THREE.Object3D, points: [number, number, number][], radius: number, material: THREE.Material): THREE.Mesh;
}>;
export type ModuleBuilder = (three: typeof THREE, spec: ModuleBuildInput, kit: SceneKit) => BuiltModule;
export type ScenePlan = Readonly<{
  inputSufficient: boolean;
  gaps: readonly { field: string; question: string }[];
  design: { title: string; decisions: readonly { description: string; basis: 'source_fact' | 'production_choice' }[]; relationships: readonly string[]; mood: string };
  modules: readonly { id: string; task: string; specs: readonly ModuleBuildInput[] }[];
  instances: readonly { id: string; module: string; specIndex: number; placement: Transform }[];
  assemblerTask: string;
}>;
export type SceneNodeDefinition = Readonly<{ id: string; instanceId: string; parentId: string | null; initialTransform: Transform }>;
export type SceneAnchor = Readonly<{ id: string; nodeId: string; pointMeters: Vec3 }>;
export type MotionState = 'closed' | 'opened';
export type MotionBase = Readonly<{ id: string; targetNodeId: string; space: 'parent-local'; initialState: MotionState; durationMs: number }>;
export type MotionSpec = MotionBase & (
  | Readonly<{ kind: 'translation'; closedPositionMeters: Vec3; openedPositionMeters: Vec3 }>
  | Readonly<{ kind: 'rotation'; closedRotation: EulerRadians; openedRotation: EulerRadians }>
);
export type SceneRequirement = Readonly<{
  id: string;
  description: string;
  relatedNodeIds: readonly string[];
  sourceFields: readonly SourceFieldRef[];
  basis: 'source_fact' | 'production_choice';
  checkBy: 'visual';
}>;
export type SceneViewpoint = Readonly<{
  id: string;
  purpose: string;
  cameraWorldPositionMeters: Vec3;
  cameraWorldTargetMeters: Vec3;
  hiddenNodeIds: readonly string[];
  motionStates: readonly { motionId: string; state: MotionState }[];
}>;
export type SceneDefinition = Readonly<{
  nodes: readonly SceneNodeDefinition[];
  bindings: readonly { nodeId: string; entityId: string }[];
  anchors: readonly SceneAnchor[];
  motions: readonly MotionSpec[];
  requirements: readonly SceneRequirement[];
  viewpoints: readonly SceneViewpoint[];
  appearance: { backgroundColor: string; exposure: number };
}>;
export type SceneAssembly = Readonly<{
  root: THREE.Group;
  nodeObjects: Readonly<Record<string, THREE.Object3D>>;
  definition: SceneDefinition;
}>;
export type SceneAssembler = (three: typeof THREE, modules: Readonly<Record<string, { buildAsset: ModuleBuilder }>>, plan: ScenePlan, kit: SceneKit) => SceneAssembly;
export type CheckIssue = Readonly<{
  stage: 'protocol' | 'runtime' | 'geometry' | 'visual' | 'overall';
  severity: 'blocking' | 'minor';
  code: string;
  message: string;
  target: { nodeId?: string; motionId?: string; moduleId?: string };
  expected?: JsonValue;
  actual?: JsonValue;
  files: readonly string[];
}>;
