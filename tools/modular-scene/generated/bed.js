export function buildAsset(THREE, spec, kit) {
  const { box, cylinder, tube, materials } = kit;
  const root = new THREE.Group();
  const w = 1.0, L = 2.1, halfW = w / 2, halfL = L / 2;
  const sx = spec.sockets;

  box(root, [w, 0.06, L], [0, 0.34, 0], materials.darkWood);
  const legs = [
    [-halfW + 0.07, -halfL + 0.07],
    [ halfW - 0.07, -halfL + 0.07],
    [-halfW + 0.07,  halfL - 0.07],
    [ halfW - 0.07,  halfL - 0.07]
  ];
  legs.forEach(p => box(root, [0.09, 0.7, 0.09], [p[0], 0.35, p[1]], materials.wood));
  box(root, [0.11, 0.7, 0.11], [-halfW + 0.07, 0.35, -halfL + 0.07], materials.darkWood);
  box(root, [0.11, 0.7, 0.11], [ halfW - 0.07, 0.35, -halfL + 0.07], materials.darkWood);
  legs.forEach(p => cylinder(root, [0.05, 0.05, 0.03], [p[0], 0.015, p[1]], materials.brass));

  box(root, [w * 0.92, 0.14, L * 0.92], [0, 0.41, 0.05], materials.cloth);
  box(root, [w * 0.94, 0.04, L * 0.94], [0, 0.50, 0.05], materials.plaster);

  const pillowMesh = box(root, [0.8, 0.10, 0.38], [0, 0.78, -0.75], materials.plaster);
  pillowMesh.rotation.y = 0.04;

  const rope = new THREE.Group();
  rope.name = 'bell_rope';
  const top = sx.rope_top, end = sx.rope_end;
  const pts = [];
  const segs = 10;
  for (let i = 0; i <= segs; i++) {
    const t = i / segs;
    pts.push([
      top[0] + (end[0] - top[0]) * t + Math.sin(t * Math.PI) * 0.05,
      top[1] + (end[1] - top[1]) * t - Math.sin(t * Math.PI) * 0.03,
      top[2] + (end[2] - top[2]) * t + Math.sin(t * Math.PI) * 0.04
    ]);
  }
  tube(rope, pts, 0.022, materials.cloth);
  cylinder(rope, [0.04, 0.04, 0.04], [top[0], top[1], top[2]], materials.brass);
  cylinder(rope, [0.035, 0.035, 0.035], [end[0], end[1], end[2]], materials.wood);
  root.add(rope);

  return {
    root,
    bindings: { bed: root, bell_rope: rope },
    sockets: {
      pillow: [sx.pillow[0], sx.pillow[1], sx.pillow[2]],
      rope_top: [top[0], top[1], top[2]],
      rope_end: [end[0], end[1], end[2]]
    },
    motions: {}
  };
}
