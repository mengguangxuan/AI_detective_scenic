import { useEffect, useRef, type ComponentRef, type RefObject } from 'react';
import { useThree } from '@react-three/fiber';
import { OrbitControls } from '@react-three/drei';
import type { SessionId } from '../../../../objects/SessionId';
import type { SceneCameraMemory } from '../../objects/SceneCameraMemory';
import { MOUSE, TOUCH, Vector3 } from 'three';

const mouseButtons = { LEFT: MOUSE.PAN, MIDDLE: MOUSE.DOLLY, RIGHT: MOUSE.ROTATE };
const touches = { ONE: TOUCH.PAN, TWO: TOUCH.DOLLY_ROTATE };
const minimum = new Vector3(-2.6, 0.3, -1.7);
const maximum = new Vector3(2.6, 2.6, 1.5);
export default function ObservationCamera({ resetVersion, memory, sessionId }: { resetVersion: number; memory: RefObject<SceneCameraMemory | null>; sessionId: SessionId }) {
  const controls = useRef<ComponentRef<typeof OrbitControls>>(null);
  const camera = useThree(state => state.camera);
  const beforeClamp = useRef(new Vector3());
  const initialized = useRef(false);
  useEffect(() => {
    if (!controls.current) return;
    const saved = resetVersion === 0 && memory.current?.sessionId === sessionId ? memory.current : null;
    camera.position.fromArray(saved?.position ?? [4, 3.5, 5]);
    controls.current.target.fromArray(saved?.target ?? [0, 0.95, 0]);
    initialized.current = true;
    controls.current.update();
    return () => { initialized.current = false; };
  }, [camera, resetVersion, memory, sessionId]);
  function constrain() {
    const orbit = controls.current;
    if (!orbit || !initialized.current) return;
    beforeClamp.current.copy(orbit.target);
    orbit.target.clamp(minimum, maximum);
    camera.position.add(beforeClamp.current.sub(orbit.target).negate());
    memory.current = { sessionId, position: [camera.position.x, camera.position.y, camera.position.z], target: [orbit.target.x, orbit.target.y, orbit.target.z] };
  }
  return <OrbitControls ref={controls} makeDefault enablePan screenSpacePanning enableDamping={false}
    mouseButtons={mouseButtons} touches={touches} minDistance={2.2} maxDistance={8}
    minPolarAngle={0.4} maxPolarAngle={1.45} minAzimuthAngle={-0.4} maxAzimuthAngle={1.45}
    onChange={constrain}/>;
}
