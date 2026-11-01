import { useEffect, useMemo, type ReactNode, type RefObject } from 'react';
import type { ThreeEvent } from '@react-three/fiber';
import { BufferGeometry, CatmullRomCurve3, Float32BufferAttribute, Vector3 } from 'three';
import type { InvestigationSnapshot } from '../../../../apis/games/objects/InvestigationSnapshot';
import type { SceneObjectBinding } from '../../../../apis/games/objects/SceneObjectBinding';
import type { SceneNodeId } from '../../../../objects/SceneNodeId';
import type { SceneCameraMemory } from '../../objects/SceneCameraMemory';
import { woodlandNodes } from '../../objects/WoodlandNodes';
import WoodlandVegetation from './WoodlandVegetation';
import WoodlandCamera from './WoodlandCamera';
import SceneItem from './SceneItem';

type Props = { snapshot: InvestigationSnapshot; cameraMemory: RefObject<SceneCameraMemory | null>; resetVersion: number; onPick: (object: SceneObjectBinding, event: ThreeEvent<MouseEvent>) => void };
function Path() {
  const geometry = useMemo(() => {
    const curve = new CatmullRomCurve3([new Vector3(-0.8, 0.025, 13), new Vector3(0.3, 0.025, 5), new Vector3(-0.3, 0.025, 1), new Vector3(0, 0.025, -1), new Vector3(1.5, 0.025, -3), new Vector3(2.8, 0.025, -4.5)]);
    const vertices: number[] = [], indices: number[] = [];
    for (let n = 0; n <= 64; n++) {
      const point = curve.getPoint(n / 64), tangent = curve.getTangent(n / 64), width = 0.62 + Math.sin(n * 0.71) * 0.08;
      vertices.push(point.x - tangent.z * width, point.y, point.z + tangent.x * width, point.x + tangent.z * width, point.y, point.z - tangent.x * width);
      if (n < 64) { const i = n * 2; indices.push(i, i + 2, i + 1, i + 1, i + 2, i + 3); }
    }
    const result = new BufferGeometry(); result.setAttribute('position', new Float32BufferAttribute(vertices, 3)); result.setIndex(indices); result.computeVertexNormals(); return result;
  }, []);
  useEffect(() => () => geometry.dispose(), [geometry]);
  return <mesh geometry={geometry} receiveShadow><meshStandardMaterial color="#9b875d" roughness={1} side={2}/></mesh>;
}
function Rocks() {
  return <>{[[ -2, 0.3, -2.5, 0.65], [-2.7, 0.18, -3, 0.4], [-1.4, 0.13, -2.1, 0.28]].map(([x, y, z, size], n) => <mesh key={n} position={[x!, y!, z!]} rotation={[0.2, n * 1.3, 0.3]} scale={[size!, size! * 0.65, size! * 0.8]} castShadow receiveShadow><icosahedronGeometry args={[1, 1]}/><meshStandardMaterial color={n === 1 ? '#68715a' : '#797a69'} roughness={1} flatShading/></mesh>)}</>;
}
const entry = { x: 0, y: 0, z: 5 };
export default function WoodlandScene({ snapshot, cameraMemory, resetVersion, onPick }: Props) {
  const { scene } = snapshot;
  const collected = snapshot.clues.some(c => c.id === 'clue_woodland_sample');
  const revealed = snapshot.observations.some(o => o.node === woodlandNodes.fragment);
  function item(node: SceneNodeId, children: ReactNode) {
    return scene.objects.some(o => o.node === node) ? <SceneItem node={node} objects={scene.objects} onPick={onPick}>{children}</SceneItem> : <group>{children}</group>;
  }
  return <>
    <color attach="background" args={['#a5b79b']}/><fog attach="fog" args={['#a5b79b', 10, 30]}/>
    <hemisphereLight args={['#dfebd2', '#49402a', 1.6]}/><ambientLight intensity={0.55}/>
    <directionalLight position={[-7, 12, -5]} color="#ffe3ac" intensity={3.2} castShadow shadow-mapSize={[1024, 1024]} shadow-camera-left={-13} shadow-camera-right={13} shadow-camera-top={13} shadow-camera-bottom={-13} shadow-normalBias={0.035}/>
    <mesh rotation={[-Math.PI / 2, 0, 0]} receiveShadow><planeGeometry args={[42, 42]}/><meshStandardMaterial color="#545c3b" roughness={1}/></mesh>
    {item(woodlandNodes.trail, <Path/>)}
    {item(woodlandNodes.clearing, <mesh position={[0, 0.019, -1]} rotation={[-Math.PI / 2, 0, 0]} scale={[1, 0.8, 1]} receiveShadow><circleGeometry args={[2.5, 40]}/><meshStandardMaterial color="#7c7950" roughness={1}/></mesh>)}
    {item(woodlandNodes.trees, <WoodlandVegetation/>)}
    {item(woodlandNodes.stones, <Rocks/>)}
    {item(woodlandNodes.leaves, <group position={[2.8, 0, -4.5]}>
      <mesh position={[1.15, 0.33, -0.1]} rotation={[0.06, 0.4, Math.PI / 2]} castShadow receiveShadow><cylinderGeometry args={[0.27, 0.36, 3.2, 9]}/><meshStandardMaterial color="#645039" roughness={1}/></mesh>
      <mesh position={[2.62, 0.3, -0.7]} rotation={[0, 0, Math.PI / 2]}><cylinderGeometry args={[0.24, 0.24, 0.03, 9]}/><meshStandardMaterial color="#bd9c62" roughness={1}/></mesh>
      <mesh position={[-0.34, 0.33, 0.52]} rotation={[0, 0.4, Math.PI / 2]}><cylinderGeometry args={[0.3, 0.3, 0.035, 9]}/><meshStandardMaterial color="#b29461" roughness={1}/></mesh>
      <mesh position={[0, 0.022, 0]} rotation={[-Math.PI / 2, 0, 0]} scale={[1.15, 0.8, 1]} receiveShadow><circleGeometry args={[0.9, 18]}/><meshStandardMaterial color={revealed ? '#6e6040' : '#9b7840'} roughness={1}/></mesh>
      {!revealed ? [0, 1, 2, 3, 4, 5, 6].map(n => <mesh key={n} position={[Math.sin(n * 2.4) * 0.4, 0.055, Math.cos(n * 2.4) * 0.35]} rotation={[0, n * 1.5, 0.1]} scale={[0.22, 0.028, 0.38]}><icosahedronGeometry args={[1, 0]}/><meshStandardMaterial color={n % 2 ? '#b3944f' : '#806538'} roughness={1}/></mesh>) : null}
    </group>)}
    {revealed && !collected ? item(woodlandNodes.fragment, <mesh position={[2.8, 0.11, -4.5]} rotation={[0.1, 0.5, 0.3]} scale={[0.22, 0.1, 0.28]} castShadow><icosahedronGeometry args={[1, 0]}/><meshStandardMaterial color="#936c35" roughness={0.38} metalness={0.12} flatShading/></mesh>) : null}
    {[-1.5, 1.4, 4].map((x, n) => <mesh key={n} position={[x - 1, 4.5, -5 - n]} rotation={[0.06, 0, -0.45]}><coneGeometry args={[0.9, 10, 20, 1, true]}/><meshBasicMaterial color="#fff0bf" transparent opacity={0.025} depthWrite={false} side={2}/></mesh>)}
    <WoodlandCamera position={scene.playerPosition ?? entry} resetVersion={resetVersion} memory={cameraMemory} sessionId={snapshot.sessionId}/>
  </>;
}
