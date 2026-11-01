import { useEffect, useRef, type ComponentRef, type RefObject } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import { OrbitControls } from '@react-three/drei';
import { MOUSE, TOUCH, Vector3 } from 'three';
import type { SceneCameraMemory } from '../../objects/SceneCameraMemory';
import type { SessionId } from '../../../../objects/SessionId';

type Position = { x: number; y: number; z: number };
const mouseButtons = { LEFT: MOUSE.PAN, MIDDLE: MOUSE.DOLLY, RIGHT: MOUSE.ROTATE };
const touches = { ONE: TOUCH.PAN, TWO: TOUCH.DOLLY_ROTATE };
export default function WoodlandCamera({ position, resetVersion, memory, sessionId }: { position: Position; resetVersion: number; memory: RefObject<SceneCameraMemory | null>; sessionId: SessionId }) {
  const controls = useRef<ComponentRef<typeof OrbitControls>>(null);
  const camera = useThree(state => state.camera);
  const invalidate = useThree(state => state.invalidate);
  const initialized = useRef(false);
  const moving = useRef(false);
  const desiredCamera = useRef(new Vector3());
  const desiredTarget = useRef(new Vector3());
  const startingCamera = useRef(new Vector3());
  const startingTarget = useRef(new Vector3());
  const startedAt = useRef(0);
  const previousTarget = useRef(new Vector3());
  const { x, z } = position;
  const stopKey = `${x}:${z}`;
  useEffect(() => {
    if (!controls.current) return;
    const saved = resetVersion === 0 && memory.current?.sessionId === sessionId && memory.current.sceneKey === 'woodland' && memory.current.stopKey === stopKey ? memory.current : null;
    const nearLog = x > 2;
    desiredCamera.current.set(x + (nearLog ? 2.3 : 4.5), nearLog ? 2.5 : 3.2, z + (nearLog ? 4.3 : 5.8));
    desiredTarget.current.set(x, nearLog ? 0.18 : 0.8, z - (nearLog ? 0 : 3.3));
    if (saved) { desiredCamera.current.fromArray(saved.position); desiredTarget.current.fromArray(saved.target); }
    const instant = !initialized.current || window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (instant) { camera.position.copy(desiredCamera.current); controls.current.target.copy(desiredTarget.current); controls.current.update(); }
    startingCamera.current.copy(camera.position); startingTarget.current.copy(controls.current.target); startedAt.current = performance.now();
    initialized.current = true; moving.current = !instant; invalidate();
  }, [camera, x, z, stopKey, resetVersion, sessionId, memory, invalidate]);
  useFrame(({ gl }) => {
    if (initialized.current) gl.domElement.dataset.sceneReady = 'woodland';
    if (!moving.current || !controls.current) return;
    const progress = Math.min(1, (performance.now() - startedAt.current) / 900), ease = 1 - (1 - progress) ** 3;
    camera.position.lerpVectors(startingCamera.current, desiredCamera.current, ease);
    controls.current.target.lerpVectors(startingTarget.current, desiredTarget.current, ease); controls.current.update();
    moving.current = progress < 1;
    if (moving.current) invalidate();
  });
  function remember() {
    const orbit = controls.current;
    if (!orbit || !initialized.current) return;
    if (!moving.current) {
      previousTarget.current.copy(orbit.target);
      orbit.target.x = Math.max(x - 2, Math.min(x + 2, orbit.target.x));
      orbit.target.y = Math.max(0.1, Math.min(2.4, orbit.target.y));
      orbit.target.z = Math.max(z - 5, Math.min(z + 1.5, orbit.target.z));
      camera.position.add(previousTarget.current.sub(orbit.target).negate());
    }
    memory.current = { sessionId, sceneKey: 'woodland', stopKey, position: [camera.position.x, camera.position.y, camera.position.z], target: [orbit.target.x, orbit.target.y, orbit.target.z] };
  }
  return <OrbitControls ref={controls} makeDefault enablePan screenSpacePanning enableDamping={false} mouseButtons={mouseButtons} touches={touches}
    minDistance={1.4} maxDistance={12} minPolarAngle={0.3} maxPolarAngle={1.48}
    onStart={() => { moving.current = false; }} onChange={remember}/>;
}
