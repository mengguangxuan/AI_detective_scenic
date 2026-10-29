import { useRef, useState, type RefObject } from 'react';
import { useFrame, type ThreeEvent } from '@react-three/fiber';
import { MathUtils, type Group } from 'three';
import type { SessionId } from '../../../../objects/SessionId';
import type { SceneCameraMemory } from '../../objects/SceneCameraMemory';
import type { InvestigationSnapshot } from '../../../../apis/games/objects/InvestigationSnapshot';
import type { SceneObjectBinding } from '../../../../apis/games/objects/SceneObjectBinding';
import { studyNodes } from '../../objects/StudyNodes';
import ObservationCamera from './ObservationCamera';
import SceneItem from './SceneItem';

type Props = { sessionId: SessionId; cameraMemory: RefObject<SceneCameraMemory | null>; scene: InvestigationSnapshot['scene']; resetVersion: number;
  onPick: (object: SceneObjectBinding, event: ThreeEvent<MouseEvent>) => void };
function Box({ position, size, color }: { position: [number, number, number]; size: [number, number, number]; color: string }) {
  return <mesh position={position} castShadow receiveShadow><boxGeometry args={size}/><meshStandardMaterial color={color} roughness={0.8}/></mesh>;
}
export default function StudyScene({ scene, resetVersion, onPick, sessionId, cameraMemory }: Props) {
  const [initialDrawerZ] = useState(() => scene.drawerOpened ? 0.75 : 0.18);
  const [initialWindowAngle] = useState(() => scene.windowOpened ? -1.15 : 0);
  const drawer = useRef<Group>(null);
  const sash = useRef<Group>(null);
  useFrame((_, delta) => {
    if (drawer.current) drawer.current.position.z = MathUtils.damp(drawer.current.position.z, scene.drawerOpened ? 0.75 : 0.18, 10, delta);
    if (sash.current) sash.current.rotation.y = MathUtils.damp(sash.current.rotation.y, scene.windowOpened ? -1.15 : 0, 10, delta);
  });
  const item = { objects: scene.objects, onPick };
  return <>
    <color attach="background" args={['#202d2b']}/>
    <ambientLight intensity={0.65}/>
    <directionalLight position={[2, 6, 4]} intensity={2.4} castShadow shadow-mapSize={[1024, 1024]}/>
    <pointLight position={[-1.6, 2.5, -0.3]} intensity={12} color="#ffc987"/>
    <Box position={[0, -0.07, 0]} size={[7, 0.14, 5]} color="#797364"/>
    <Box position={[-1.4, 1.6, -2.3]} size={[4.2, 3.2, 0.12]} color="#52645e"/>
    <Box position={[2.8, 1.6, -2.3]} size={[1.4, 3.2, 0.12]} color="#52645e"/>
    <Box position={[1.4, 0.65, -2.3]} size={[1.4, 1.3, 0.12]} color="#52645e"/>
    <Box position={[1.4, 2.95, -2.3]} size={[1.4, 0.5, 0.12]} color="#52645e"/>
    <Box position={[1.4, 1.7, -2.8]} size={[1.6, 2, 0.08]} color="#6e8d6c"/>
    <Box position={[-3.4, 1.6, 0]} size={[0.12, 3.2, 4.6]} color="#384f48"/>
    <Box position={[0, 0.12, -2.18]} size={[6.8, 0.18, 0.08]} color="#26352f"/>
    <SceneItem node={studyNodes.bookshelf} {...item}><Box position={[-2.5, 1.05, -1.7]} size={[1, 2.1, 0.52]} color="#3b3026"/>
    {[0.45, 1.05, 1.65].map(y => <group key={y}><Box position={[-2.5, y, -1.38]} size={[1.05, 0.06, 0.58]} color="#67513a"/>{[0, 1, 2, 3, 4].map(n => <Box key={n} position={[-2.82 + n * 0.16, y + 0.23, -1.55]} size={[0.12, 0.4, 0.22]} color={['#ad8a5a','#62796a','#8b5846'][n % 3] ?? '#ad8a5a'}/>)}</group>)}
    </SceneItem>
    <SceneItem node={studyNodes.window} {...item}>
      <Box position={[0.7, 2, -2.15]} size={[0.09, 1.48, 0.12]} color="#b8a58b"/>
      <Box position={[2.1, 2, -2.15]} size={[0.09, 1.48, 0.12]} color="#b8a58b"/>
      <Box position={[1.4, 1.3, -2.15]} size={[1.48, 0.09, 0.12]} color="#b8a58b"/>
      <Box position={[1.4, 2.7, -2.15]} size={[1.48, 0.09, 0.12]} color="#b8a58b"/>
      <group ref={sash} position={[0.7, 2, -2.08]} rotation={[0, initialWindowAngle, 0]}>
        <Box position={[0.7, 0, 0]} size={[1.32, 1.32, 0.04]} color="#829e9c"/>
        <Box position={[0.7, 0, 0.03]} size={[0.045, 1.32, 0.05]} color="#ded1b4"/>
        <Box position={[0.7, 0, 0.03]} size={[1.32, 0.045, 0.05]} color="#ded1b4"/>
        <Box position={[1.3, -0.1, 0.07]} size={[0.04, 0.2, 0.05]} color="#c9ad73"/>
      </group>
    </SceneItem>
    <SceneItem node={studyNodes.desk} {...item}>
      <Box position={[0, 1.18, 0]} size={[2.8, 0.15, 1.35]} color="#76543b"/>
      {[-1.15, 1.15].flatMap(x => [-0.45, 0.45].map(z => <Box key={`${x}:${z}`} position={[x, 0.55, z]} size={[0.12, 1.1, 0.12]} color="#4a382a"/>))}
      <Box position={[0.8, 0.94, -0.38]} size={[1, 0.37, 0.15]} color="#44342a"/>
      <SceneItem node={studyNodes.drawer} {...item}><group ref={drawer} position={[0.8, 0.9, initialDrawerZ]}>
        <Box position={[0, -0.06, -0.25]} size={[0.88, 0.06, 0.92]} color="#9b7750"/>
        <Box position={[0, 0.04, 0.23]} size={[1, 0.3, 0.09]} color="#715139"/>
        <Box position={[0, 0.04, 0.3]} size={[0.27, 0.045, 0.06]} color="#d5b779"/>
      </group></SceneItem>
      <SceneItem node={studyNodes.clock} {...item}><group position={[0.45, 1.5, -0.32]}>
        <Box position={[0, -0.07, 0]} size={[0.48, 0.48, 0.18]} color="#493629"/>
        <mesh position={[0, 0, 0.1]} rotation={[Math.PI / 2, 0, 0]}><cylinderGeometry args={[0.19, 0.19, 0.02, 32]}/><meshStandardMaterial color="#e8dabc"/></mesh>
        <group rotation={[0, 0, -Math.PI * 2 * (6 + 22 / 60) / 12]}><Box position={[0, 0.05, 0.12]} size={[0.025, 0.1, 0.015]} color="#302820"/></group>
        <group rotation={[0, 0, -Math.PI * 2 * 22 / 60]}><Box position={[0, 0.075, 0.13]} size={[0.015, 0.15, 0.015]} color="#302820"/></group>
      </group></SceneItem>
      <SceneItem node={studyNodes.paper} {...item}><group position={[0, 1.27, 0.12]}>
        <mesh rotation={[-Math.PI / 2, 0, 0]}><planeGeometry args={[0.48, 0.32]}/><meshStandardMaterial color="#b9a578" transparent opacity={0.3} side={2}/></mesh>
      </group></SceneItem>
      <SceneItem node={studyNodes.telephone} {...item}><group position={[-0.65, 1.32, -0.3]}>
        <Box position={[0, 0, 0]} size={[0.42, 0.12, 0.28]} color="#282723"/>
        <Box position={[0, 0.16, 0]} size={[0.46, 0.07, 0.1]} color="#25241f"/>
      </group></SceneItem>
      <SceneItem node={studyNodes.lamp} {...item}><mesh position={[-1, 1.32, 0.2]}><cylinderGeometry args={[0.16, 0.2, 0.1, 24]}/><meshStandardMaterial color="#b69b68"/></mesh>
      <mesh position={[-1, 1.62, 0.2]}><cylinderGeometry args={[0.025, 0.025, 0.6, 12]}/><meshStandardMaterial color="#b69b68"/></mesh>
      <mesh position={[-1, 1.95, 0.2]}><coneGeometry args={[0.38, 0.33, 24, 1, true]}/><meshStandardMaterial color="#b69a68" side={2}/></mesh>
      </SceneItem>
    </SceneItem>
    <ObservationCamera resetVersion={resetVersion} sessionId={sessionId} memory={cameraMemory}/>
  </>;
}
