import { useRef, useState } from 'react';
import { useFrame, type ThreeEvent } from '@react-three/fiber';
import { MathUtils, type Group } from 'three';
import type { SceneObjectBinding } from '../../../../apis/games/objects/SceneObjectBinding';
import { studyNodes } from '../../objects/StudyNodes';
import SceneItem from './SceneItem';

type Props = { objects: SceneObjectBinding[]; onPick: (object: SceneObjectBinding, event: ThreeEvent<MouseEvent>) => void };
export default function Planter({ objects, onPick }: Props) {
  const lifted = objects.some(o => o.node === studyNodes.planterInterior);
  const [initialHeight] = useState(() => lifted ? 1.65 : 1.12);
  const inner = useRef<Group>(null);
  useFrame((_, delta) => {
    if (inner.current) inner.current.position.y = MathUtils.damp(inner.current.position.y, lifted ? 1.65 : 1.12, 10, delta);
  });
  const item = { objects, onPick };
  return <SceneItem node={studyNodes.planter} {...item}>
    <group position={[2.35, 0, -1.4]}>
      {[[-0.23, -0.23], [-0.23, 0.23], [0.23, -0.23], [0.23, 0.23]].map(([x, z], i) =>
        <mesh key={i} position={[x!, 0.42, z!]} castShadow><boxGeometry args={[0.055, 0.84, 0.055]}/><meshStandardMaterial color="#514332"/></mesh>)}
      <mesh position={[0, 0.85, 0]} receiveShadow><cylinderGeometry args={[0.39, 0.39, 0.08, 24]}/><meshStandardMaterial color="#69523a"/></mesh>
      <mesh position={[0, 1.05, 0]} castShadow><cylinderGeometry args={[0.36, 0.28, 0.34, 24, 1, true]}/><meshStandardMaterial color="#a8845e" side={2}/></mesh>
      <mesh position={[0, 1.22, 0]} rotation={[Math.PI / 2, 0, 0]}><torusGeometry args={[0.36, 0.025, 8, 32]}/><meshStandardMaterial color="#c1a17a"/></mesh>
      {lifted && <SceneItem node={studyNodes.planterInterior} {...item}>
        <mesh position={[0, 0.9, 0]} receiveShadow><cylinderGeometry args={[0.28, 0.28, 0.025, 24]}/><meshStandardMaterial color="#695342"/></mesh>
        <mesh position={[0, 1.05, 0]}><cylinderGeometry args={[0.335, 0.27, 0.3, 24, 1, true]}/><meshStandardMaterial color="#80674e" side={2}/></mesh>
      </SceneItem>}
      <group ref={inner} position={[0, initialHeight, 0]}>
        <mesh castShadow><cylinderGeometry args={[0.25, 0.19, 0.3, 24]}/><meshStandardMaterial color="#715543"/></mesh>
        <mesh position={[0, 0.155, 0]}><cylinderGeometry args={[0.245, 0.245, 0.015, 24]}/><meshStandardMaterial color="#40372a"/></mesh>
        <mesh position={[0, 0.49, 0]} castShadow><cylinderGeometry args={[0.022, 0.035, 0.67, 10]}/><meshStandardMaterial color="#68704a"/></mesh>
        {[0, 1, 2, 3, 4, 5].map(i => <group key={i} rotation={[0, i * Math.PI / 3, 0]}>
          <mesh position={[0.21, 0.77, 0]} rotation={[0, 0, -0.55]} castShadow scale={[0.4, 0.055, 0.1]}>
            <sphereGeometry args={[1, 12, 8]}/><meshStandardMaterial color={i % 2 ? '#536b43' : '#70844e'}/>
          </mesh>
        </group>)}
      </group>
    </group>
  </SceneItem>;
}
