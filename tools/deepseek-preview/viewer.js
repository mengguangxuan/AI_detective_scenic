import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { createRoom } from './approved-room.js';
const status = document.querySelector('#status');
try {
  const result = createRoom(THREE);
  const { root, objects, drawerMotion } = result;
  const ids = ['bed','chest','drawer','dressing_table','chair_1','chair_2','rug','fireplace','window','shutter','bell_rope','vent'];
  if (!(root instanceof THREE.Group) || ids.some(id => !(objects[id] instanceof THREE.Object3D))) throw new Error('生成代码缺少约定物体');
  for (const id of ids) { let node=objects[id]; while (node && node !== root) node=node.parent; if (!node) throw new Error('物体不属于场景：'+id); }
  const scene = new THREE.Scene(); scene.background = new THREE.Color('#e9e2d5'); scene.add(root);
  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setPixelRatio(Math.min(devicePixelRatio,1.5)); renderer.setSize(innerWidth,innerHeight);
  renderer.outputColorSpace=THREE.SRGBColorSpace; renderer.toneMapping=THREE.ACESFilmicToneMapping; renderer.toneMappingExposure=1.15;
  document.querySelector('#view').appendChild(renderer.domElement);
  const camera=new THREE.PerspectiveCamera(48,innerWidth/innerHeight,0.05,100);
  const controls=new OrbitControls(camera,renderer.domElement); controls.enableDamping=true; controls.maxDistance=20;controls.minDistance=.35;
  function overview(){camera.position.set(7,6,8);controls.target.set(0,1,0);controls.update();}
  const cutaways=[];root.traverse(o=>{if(o.userData.cutaway || /^(wall_right|wainscot_right|door_frame|door_panel|hall_shadow|skirting_right)/.test(o.name))cutaways.push(o);});let cutaway=true;cutaways.forEach(o=>o.visible=false);
  document.querySelector('#cutaway').onclick=()=>{cutaway=!cutaway;cutaways.forEach(o=>o.visible=!cutaway);document.querySelector('#cutaway').textContent=cutaway?'显示完整墙顶':'剖开墙顶';};
  const descriptions={bed:['窄单人床','可见白色床罩、床架与枕头。床的位置应与拉绳和通气孔保持一致。'],chest:['棕色抽屉柜','抽屉可以独立打开，观察里面的空间。'],drawer:['抽屉','关闭时内部被遮挡，打开后可查看。当前没有放置任何案件线索。'],dressing_table:['梳妆台','查看镜面、桌面与支架。'],chair_1:['藤椅','查看椅背、座面和支撑。'],chair_2:['藤椅','查看椅背、座面和支撑。'],rug:['方形地毯','位于房间中央。'],fireplace:['壁炉','查看炉口、边框与炉膛。'],window:['窗户','查看窗格与开口位置。'],shutter:['百叶窗','本次只展示造型。'],bell_rope:['床边拉绳','观察它与枕头和上方孔口的位置关系。'],vent:['通气孔','观察孔口及其所在的侧墙。']};
  let selected=null,opened=false;const helper=new THREE.BoxHelper(objects.bed,0xd09b3f);helper.visible=false;scene.add(helper);
  function select(id){selected=id;document.querySelector('#selected').textContent=descriptions[id][0];document.querySelector('#detail').textContent=descriptions[id][1];helper.setFromObject(objects[id]);helper.visible=true;}
  function focus(id){select(id);const box=new THREE.Box3().setFromObject(objects[id]);const c=box.getCenter(new THREE.Vector3());const direction=c.clone().multiplyScalar(-1);direction.y=0;if(direction.length()<.2)direction.set(0,0,1);direction.normalize();camera.position.copy(c).addScaledVector(direction,2.6);camera.position.y=Math.max(c.y+1,1.6);controls.target.copy(c);controls.update();}
  document.querySelector('#overview').onclick=overview;document.querySelector('#bedview').onclick=()=>focus('bed');document.querySelector('#chestview').onclick=()=>focus('chest');
  const drawerButton=document.querySelector('#drawer');drawerButton.disabled=false;drawerButton.onclick=()=>{opened=!opened;objects.drawer.position.fromArray(opened?drawerMotion.opened:drawerMotion.closed);drawerButton.textContent=opened?'关闭抽屉':'打开抽屉';select('drawer');document.body.dataset.drawer=opened?'open':'closed';};
  const ray=new THREE.Raycaster();const pointer=new THREE.Vector2();let down;
  renderer.domElement.addEventListener('pointerdown',event=>{down=[event.clientX,event.clientY];});
  renderer.domElement.addEventListener('pointerup',event=>{if(!down||Math.hypot(event.clientX-down[0],event.clientY-down[1])>5)return;const rect=renderer.domElement.getBoundingClientRect();pointer.set((event.clientX-rect.left)/rect.width*2-1,-(event.clientY-rect.top)/rect.height*2+1);ray.setFromCamera(pointer,camera);const hit=ray.intersectObject(root,true).find(h=>{let o=h.object;while(o){if(!o.visible)return false;o=o.parent;}return true;});if(!hit)return;let node=hit.object;while(node&&node!==root){const id=ids.find(id=>objects[id]===node);if(id){select(id);break;}node=node.parent;}});
  overview();renderer.setAnimationLoop(()=>{controls.update();if(selected)helper.update();renderer.render(scene,camera);});
  addEventListener('resize',()=>{camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();renderer.setSize(innerWidth,innerHeight);});
  status.textContent='场景已加载 · 可检查物体和开关抽屉';document.body.dataset.state='ready';document.body.dataset.drawer='closed';
  window.roomPreview={...result,camera,controls,renderer,scene,select,focus};
} catch(error){status.textContent='加载失败：'+error.message;document.body.dataset.state='error';console.error(error);}
