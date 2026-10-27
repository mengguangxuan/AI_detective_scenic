import { useRef } from 'react';
import { useFrame, type ThreeEvent } from '@react-three/fiber';
import { Html, OrbitControls } from '@react-three/drei';
import { MathUtils, type Group } from 'three';
import type { PreviewPointId } from '../../objects/PreviewPointId';

type Props = { drawerOpened: boolean; selected: PreviewPointId | null; onSelect: (point: PreviewPointId) => void };
function Box({ position, size, color }: { position: [number, number, number]; size: [number, number, number]; color: string }) {
  return <mesh position={position} castShadow receiveShadow><boxGeometry args={size}/><meshStandardMaterial color={color} roughness={0.8}/></mesh>;
}
export default function StudyScene({ drawerOpened, selected, onSelect }: Props) {
  const drawer = useRef<Group>(null);
  useFrame((_, delta) => {
    if (drawer.current) drawer.current.position.z = MathUtils.damp(drawer.current.position.z, drawerOpened ? 0.75 : 0.18, 10, delta);
  });
  function pick(event: ThreeEvent<MouseEvent>, id: PreviewPointId) { event.stopPropagation(); onSelect(id); }
  return <>
    <color attach="background" args={['#202d2b']}/>
    <ambientLight intensity={0.65}/>
    <directionalLight position={[2, 6, 4]} intensity={2.4} castShadow shadow-mapSize={[1024, 1024]}/>
    <pointLight position={[-1.6, 2.5, -0.3]} intensity={12} color="#ffc987"/>
    <Box position={[0, -0.07, 0]} size={[7, 0.14, 5]} color="#797364"/>
    <Box position={[0, 1.6, -2.3]} size={[7, 3.2, 0.12]} color="#52645e"/>
    <Box position={[-3.4, 1.6, 0]} size={[0.12, 3.2, 4.6]} color="#384f48"/>
    <Box position={[0, 0.12, -2.18]} size={[6.8, 0.18, 0.08]} color="#26352f"/>
    <Box position={[-2.5, 1.05, -1.7]} size={[1, 2.1, 0.52]} color="#3b3026"/>
    {[0.45, 1.05, 1.65].map(y => <group key={y}><Box position={[-2.5, y, -1.38]} size={[1.05, 0.06, 0.58]} color="#67513a"/>{[0, 1, 2, 3, 4].map(n => <Box key={n} position={[-2.82 + n * 0.16, y + 0.23, -1.55]} size={[0.12, 0.4, 0.22]} color={['#ad8a5a','#62796a','#8b5846'][n % 3] ?? '#ad8a5a'}/>)}</group>)}
    <Box position={[1.4, 2, -2.2]} size={[1.4, 1.4, 0.08]} color="#b8a58b"/>
    <Box position={[1.4, 2, -2.14]} size={[1.18, 1.18, 0.04]} color="#829e9c"/>
    <Box position={[1.4, 2, -2.09]} size={[0.045, 1.2, 0.05]} color="#ded1b4"/>
    <Box position={[1.4, 2, -2.09]} size={[1.2, 0.045, 0.05]} color="#ded1b4"/>
    <group position={[0, 0, 0]}>
      <Box position={[0, 1.18, 0]} size={[2.8, 0.15, 1.35]} color="#76543b"/>
      {[-1.15, 1.15].flatMap(x => [-0.45, 0.45].map(z => <Box key={`${x}:${z}`} position={[x, 0.55, z]} size={[0.12, 1.1, 0.12]} color="#4a382a"/>))}
      <Box position={[0.8, 0.94, -0.38]} size={[1, 0.37, 0.15]} color="#44342a"/>
      <group ref={drawer} position={[0.8, 0.9, drawerOpened ? 0.75 : 0.18]} onClick={e => pick(e, 'point_drawer')}>
        <Box position={[0, -0.06, -0.25]} size={[0.88, 0.06, 0.92]} color="#9b7750"/>
        <Box position={[0, 0.04, 0.23]} size={[1, 0.3, 0.09]} color={selected === 'point_drawer' ? '#bc945c' : '#715139'}/>
        <Box position={[0, 0.04, 0.3]} size={[0.27, 0.045, 0.06]} color="#d5b779"/>
        {drawerOpened ? <mesh position={[0, -0.017, -0.13]} rotation={[-Math.PI/2, 0, 0.08]} onClick={e => pick(e, 'point_letter')}><planeGeometry args={[0.48, 0.31]}/><meshStandardMaterial color={selected === 'point_letter' ? '#fff4b7' : '#e7dcc5'} side={2}/></mesh> : null}
        <Html position={[0, 0.32, 0.3]} center><button type="button" aria-label="在场景中选择书桌抽屉" onClick={() => onSelect('point_drawer')} className="h-7 w-7 rounded-full border border-amber-100 bg-stone-900/80 text-sm text-amber-100 shadow-lg hover:bg-amber-900">1</button></Html>
        {drawerOpened ? <Html position={[0, 0.08, -0.13]} center><button type="button" aria-label="在场景中选择信件" onClick={() => onSelect('point_letter')} className="h-7 w-7 rounded-full border border-amber-100 bg-stone-900/80 text-sm text-amber-100 hover:bg-amber-900">2</button></Html> : null}
      </group>
      <Box position={[-0.8, 1.3, -0.2]} size={[0.5, 0.08, 0.35]} color="#3f625b"/>
      <mesh position={[-1, 1.32, 0.2]}><cylinderGeometry args={[0.16, 0.2, 0.1, 24]}/><meshStandardMaterial color="#b69b68"/></mesh>
      <mesh position={[-1, 1.62, 0.2]}><cylinderGeometry args={[0.025, 0.025, 0.6, 12]}/><meshStandardMaterial color="#b69b68"/></mesh>
      <mesh position={[-1, 1.95, 0.2]}><coneGeometry args={[0.38, 0.33, 24, 1, true]}/><meshStandardMaterial color="#b69a68" side={2}/></mesh>
    </group>
    <OrbitControls makeDefault target={[0, 0.95, 0]} minDistance={3} maxDistance={8} minPolarAngle={0.4} maxPolarAngle={1.5} enablePan={false}/>
  </>;
}
