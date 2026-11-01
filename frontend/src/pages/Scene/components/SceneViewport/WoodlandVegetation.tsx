import { useLayoutEffect, useRef } from 'react';
import { Color, Object3D, type InstancedMesh } from 'three';

type Instance = { position: [number, number, number]; scale: [number, number, number]; rotation?: [number, number, number]; color: string };
function random(seed: number) { const value = Math.sin(seed * 127.1 + 311.7) * 43758.5453; return value - Math.floor(value); }
const bark = ['#4b4030', '#63533a', '#756344'];
const foliage = ['#35523a', '#456342', '#597449', '#687c49', '#708153'];
const trunks: Instance[] = [];
const crowns: Instance[] = [];
for (let n = 0; n < 76; n++) {
  const side = n % 2 ? -1 : 1;
  const x = side * (3.5 + random(n + 1) * 8.5), z = -14 + random(n + 90) * 27;
  if (Math.hypot(x - 2.8, z + 4.5) < 2) continue;
  const height = 4.1 + random(n + 190) * 3.4, width = 0.19 + random(n + 290) * 0.15;
  trunks.push({ position: [x, height / 2, z], scale: [width, height, width], rotation: [0, 0, (random(n + 390) - 0.5) * 0.13], color: bark[n % bark.length]! });
  for (let j = 0; j < 3; j++) crowns.push({ position: [x + (j - 1) * 0.9, height - 0.1 + (j % 2) * 0.7, z + (j - 1) * 0.5],
    scale: [1.6 + random(n + j + 490), 1.2 + random(n + j + 590), 1.6 + random(n + j + 690)], color: foliage[(n + j) % foliage.length]! });
}
const grass: Instance[] = Array.from({ length: 360 }, (_, n) => {
  const x = (random(n + 800) - 0.5) * 20, z = (random(n + 1200) - 0.5) * 26;
  return { position: [Math.abs(x) < 1.2 ? x + 2.1 : x, 0.13, z], scale: [0.12 + random(n + 1600) * 0.15, 0.2 + random(n + 1800) * 0.2, 0.09], rotation: [0, random(n + 2000) * Math.PI, -0.2], color: foliage[n % foliage.length]! };
});
const litter: Instance[] = Array.from({ length: 440 }, (_, n) => ({ position: [(random(n + 2400) - 0.5) * 22, 0.035, (random(n + 2900) - 0.5) * 26],
  scale: [0.07 + random(n + 3400) * 0.12, 0.025, 0.15 + random(n + 3900) * 0.12], rotation: [0, random(n + 4400) * Math.PI, 0], color: ['#998044', '#6e6738', '#b19b55', '#51492d'][n % 4]! }));

function useInstances(instances: readonly Instance[]) {
  const ref = useRef<InstancedMesh>(null);
  useLayoutEffect(() => {
    if (!ref.current) return;
    const item = new Object3D(), color = new Color();
    instances.forEach((instance, index) => {
      item.position.set(...instance.position); item.scale.set(...instance.scale); item.rotation.set(...(instance.rotation ?? [0, 0, 0])); item.updateMatrix();
      ref.current!.setMatrixAt(index, item.matrix); ref.current!.setColorAt(index, color.set(instance.color));
    });
    ref.current.instanceMatrix.needsUpdate = true;
    if (ref.current.instanceColor) ref.current.instanceColor.needsUpdate = true;
    ref.current.computeBoundingSphere();
  }, [instances]);
  return ref;
}
export default function WoodlandVegetation() {
  const trunkRef = useInstances(trunks), crownRef = useInstances(crowns), grassRef = useInstances(grass), leafRef = useInstances(litter);
  return <>
    <instancedMesh ref={trunkRef} args={[undefined, undefined, trunks.length]} castShadow receiveShadow><cylinderGeometry args={[0.72, 1, 1, 7]}/><meshStandardMaterial roughness={1} flatShading/></instancedMesh>
    <instancedMesh ref={crownRef} args={[undefined, undefined, crowns.length]} castShadow receiveShadow><icosahedronGeometry args={[1, 1]}/><meshStandardMaterial roughness={1} flatShading/></instancedMesh>
    <instancedMesh ref={grassRef} args={[undefined, undefined, grass.length]} receiveShadow><coneGeometry args={[1, 1, 3]}/><meshStandardMaterial roughness={1} flatShading/></instancedMesh>
    <instancedMesh ref={leafRef} args={[undefined, undefined, litter.length]} receiveShadow><icosahedronGeometry args={[1, 0]}/><meshStandardMaterial roughness={1} flatShading/></instancedMesh>
  </>;
}
