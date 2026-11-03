import { useEffect, useRef, type ComponentRef, type RefObject } from 'react';
import { useThree, type ThreeEvent } from '@react-three/fiber';
import { OrbitControls, Html } from '@react-three/drei';
import { MOUSE } from 'three';
import type { InvestigationSnapshot } from '../../../../apis/games/objects/InvestigationSnapshot';
import type { SceneObjectBinding } from '../../../../apis/games/objects/SceneObjectBinding';
import type { SceneCameraMemory } from '../../objects/SceneCameraMemory';
import SceneItem from './SceneItem';
type Props = { snapshot: InvestigationSnapshot; cameraMemory: RefObject<SceneCameraMemory | null>; resetVersion: number;
  onPick: (object: SceneObjectBinding, event: ThreeEvent<MouseEvent>) => void };
export default function AuthoredScene({ snapshot, cameraMemory, resetVersion, onPick }: Props) {
  const render = snapshot.scene.render!;
  const controls = useRef<ComponentRef<typeof OrbitControls>>(null);
  const camera = useThree(state => state.camera);
  const key = `${snapshot.scene.bundleId}:${render.room.place}`;
  const sessionId = snapshot.sessionId;
  const { room } = render;
  useEffect(() => {
    const saved = resetVersion === 0 && cameraMemory.current?.sessionId === sessionId && cameraMemory.current.sceneKey === key ? cameraMemory.current : null;
    camera.position.set(...(saved?.position ?? [room.cameraPosition.x, room.cameraPosition.y, room.cameraPosition.z]));
    controls.current?.target.set(...(saved?.target ?? [room.cameraTarget.x, room.cameraTarget.y, room.cameraTarget.z]));
    controls.current?.update();
  }, [camera, cameraMemory, key, sessionId, resetVersion, room]);
  return <>
    <color attach="background" args={['#253531']}/><ambientLight intensity={1.2}/>
    <directionalLight position={[3, 9, 4]} intensity={2.5} castShadow/>
    <mesh position={[0, -room.floorSize.y / 2, 0]} receiveShadow><boxGeometry args={[room.floorSize.x, room.floorSize.y, room.floorSize.z]}/><meshStandardMaterial color={room.color}/></mesh>
    {render.instances.map(instance => <group key={instance.node} position={[instance.position.x, instance.position.y, instance.position.z]}>
      <SceneItem node={instance.node} objects={snapshot.scene.objects} onPick={onPick}>
        {instance.parts.map((part, index) => <mesh key={index} position={[part.offset.x, part.offset.y, part.offset.z]} scale={part.shape === 'Sphere' ? [part.size.x, part.size.y, part.size.z] : [1, 1, 1]} castShadow receiveShadow>
          {part.shape === 'Box' ? <boxGeometry args={[part.size.x, part.size.y, part.size.z]}/> : part.shape === 'Cylinder' ? <cylinderGeometry args={[part.size.x / 2, part.size.z / 2, part.size.y, 24]}/> : <sphereGeometry args={[0.5, 20, 16]}/>}
          <meshStandardMaterial color={part.color} roughness={0.8}/>
        </mesh>)}
        <Html center position={[0, Math.max(...instance.parts.map(p => p.offset.y + p.size.y / 2)) + 0.2, 0]} style={{ pointerEvents: 'none', whiteSpace: 'nowrap' }}>
          <span className="rounded bg-stone-950/80 px-2 py-1 text-xs text-amber-100">{snapshot.scene.objects.find(o => o.node === instance.node)?.label}</span>
        </Html>
      </SceneItem>
    </group>)}
    <OrbitControls ref={controls} makeDefault mouseButtons={{ LEFT: MOUSE.PAN, MIDDLE: MOUSE.DOLLY, RIGHT: MOUSE.ROTATE }} minDistance={2} maxDistance={18} maxPolarAngle={1.5} onChange={() => {
      const target = controls.current?.target;
      if (target) cameraMemory.current = { sessionId, sceneKey: key, position: [camera.position.x, camera.position.y, camera.position.z], target: [target.x, target.y, target.z] };
    }}/>
  </>;
}
