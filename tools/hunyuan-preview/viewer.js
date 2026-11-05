import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { SparkRenderer, SplatMesh } from '@sparkjsdev/spark';

const status = document.querySelector('#status');
let loaded = false;
function fail(error) {
  status.textContent = '3D加载失败，可点击“查看全景图”查看已生成画面。' + (error instanceof Error ? ' ' + error.message : '');
  document.body.dataset.state = 'error';
}
window.addEventListener('unhandledrejection', event => fail(event.reason));
const poster = document.querySelector('#poster');
document.querySelector('#picture').onclick = () => { poster.style.display = poster.style.display === 'flex' ? 'none' : 'flex'; };
document.querySelector('#full').onclick = () => { (document.fullscreenElement ? document.exitFullscreen() : document.documentElement.requestFullscreen()).catch(fail); };
try {
  const lite = new URLSearchParams(location.search).has('lite');
  const renderer = new THREE.WebGLRenderer({ antialias: false });
  renderer.setPixelRatio(lite ? 0.75 : Math.min(devicePixelRatio, 1.25));
  renderer.setSize(innerWidth, innerHeight);
  document.querySelector('#view').appendChild(renderer.domElement);
  const scene = new THREE.Scene();
  scene.background = new THREE.Color('#151917');
  const camera = new THREE.PerspectiveCamera(65, innerWidth / innerHeight, 0.02, 500);
  const controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.minDistance = 0.1;
  controls.maxDistance = 60;
  let dirty = true;
  controls.addEventListener("change", () => { dirty = true; });
  const spark = new SparkRenderer({ renderer, lodSplatCount: lite ? 100000 : 350000, onDirty: () => { dirty = true; } });
  scene.add(spark);
  const p = await fetch('/position.json').then(r => { if (!r.ok) throw new Error('场景坐标读取失败'); return r.json(); });
  const rotation = new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(...p.up_direction).normalize(), new THREE.Vector3(0, 1, 0));
  const start = new THREE.Vector3(...p.center_point).applyQuaternion(rotation);
  const forward = new THREE.Vector3(...p.facing_direction).applyQuaternion(rotation).normalize();
  const splats = new SplatMesh({ url: '/scene.spz', lod: true });
  splats.quaternion.copy(rotation);
  scene.add(splats);
  function inside() {
    poster.style.display = 'none';
    camera.position.copy(start);
    controls.target.copy(start).addScaledVector(forward, 3);
    controls.update();
  }
  function outside() {
    poster.style.display = 'none';
    const box = splats.getBoundingBox().applyMatrix4(splats.matrixWorld);
    const center = box.getCenter(new THREE.Vector3());
    const distance = Math.max(box.getSize(new THREE.Vector3()).length() * 0.7, 3);
    camera.position.copy(center).add(new THREE.Vector3(distance * 0.7, distance * 0.4, distance * 0.7));
    controls.target.copy(center);
    controls.update();
  }
  document.querySelector('#inside').onclick = inside;
  document.querySelector('#outside').onclick = () => { if (loaded) outside(); };
  inside();
  const keys = new Set();
  addEventListener('keydown', event => { if (!event.ctrlKey && !event.metaKey && ['w','a','s','d','q','e'].includes(event.key.toLowerCase())) keys.add(event.key.toLowerCase()); });
  addEventListener('keyup', event => keys.delete(event.key.toLowerCase()));
  addEventListener('blur', () => keys.clear());
  const clock = new THREE.Clock();
  let lastFrame = 0;
  renderer.setAnimationLoop(time => {
    if (time - lastFrame < 33) return;
    lastFrame = time;
    const dt = Math.min(clock.getDelta(), 0.05);
    if (loaded && poster.style.display !== 'flex' && keys.size) {
      const movement = new THREE.Vector3((keys.has('d') ? 1 : 0) - (keys.has('a') ? 1 : 0), 0, (keys.has('s') ? 1 : 0) - (keys.has('w') ? 1 : 0));
      movement.applyQuaternion(camera.quaternion);
      movement.y += (keys.has('e') ? 1 : 0) - (keys.has('q') ? 1 : 0);
      movement.multiplyScalar(dt * 2);
      camera.position.add(movement);
      controls.target.add(movement);
    }
    controls.update();
    if (dirty) {
      dirty = false;
      renderer.render(scene, camera);
    }
  });
  addEventListener('resize', () => { camera.aspect = innerWidth / innerHeight; camera.updateProjectionMatrix(); renderer.setSize(innerWidth, innerHeight); });
  await splats.initialized;
  loaded = true;
  dirty = true;
  status.textContent = '3D场景已加载 · 可以旋转、缩放和移动。当前为自由浏览，尚未配置行走碰撞或案件操作。';
  document.body.dataset.state = 'ready';
  window.preview = { camera, controls, splats, inside, outside, renderer, scene };
} catch (error) { fail(error); }
