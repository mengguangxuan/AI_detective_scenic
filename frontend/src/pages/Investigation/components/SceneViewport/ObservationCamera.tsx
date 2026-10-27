import { useEffect, useRef, type ComponentRef } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import { OrbitControls } from '@react-three/drei';
import { Vector3 } from 'three';
import { studyFocus } from '../../functions/studyFocus';
import type { SceneSelection } from '../../objects/SceneSelection';
import type { StudyFocus } from '../../objects/StudyFocus';
const targets: Record<StudyFocus, readonly [number, number, number]> = {
  overview: [0, 0.95, 0], desk: [0, 1.18, 0], drawer: [0.8, 0.9, 0.6], letter: [0.8, 0.9, 0.65],
  lamp: [-1, 1.6, 0.2], window: [1.4, 2, -2.2], bookshelf: [-2.5, 1.1, -1.7],
};
export default function ObservationCamera({ focus: selection }: { focus: SceneSelection }) {
  const controls = useRef<ComponentRef<typeof OrbitControls>>(null);
  const camera = useThree(state => state.camera);
  const transition = useRef<{ target: Vector3; eye: Vector3; remaining: number } | null>(null);
  useEffect(() => {
    const focus = studyFocus(selection);
    const target = new Vector3(...targets[focus]);
    const offset = focus === 'overview' ? new Vector3(4, 2.55, 5) : new Vector3(2.4, 1.3, 3);
    transition.current = { target, eye: target.clone().add(offset), remaining: 0.8 };
  }, [selection]);
  useFrame((_, delta) => {
    const next = transition.current;
    if (!next || !controls.current) return;
    next.remaining -= delta;
    const alpha = next.remaining <= 0 ? 1 : 1 - Math.exp(-8 * delta);
    controls.current.target.lerp(next.target, alpha);
    camera.position.lerp(next.eye, alpha);
    controls.current.update();
    if (next.remaining <= 0) transition.current = null;
  });
  return <OrbitControls ref={controls} makeDefault minDistance={3} maxDistance={8} minPolarAngle={0.4} maxPolarAngle={1.5} enablePan={false} onStart={() => { transition.current = null; }}/>;
}
