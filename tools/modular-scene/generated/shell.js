export function buildAsset(THREE, spec, kit) {
  const root = new THREE.Group();
  const registry = {};

  const W = 6, H = 2.8, D = 5;
  const wallT = 0.08;
  const halfW = W / 2, halfD = D / 2;
  const wallH = H, wallY = H / 2;

  const wallMat = kit.materials.plaster;
  const floorMat = kit.materials.wood;
  const ceilMat = kit.materials.plaster;
  const trimMat = kit.materials.darkWood;
  const glassMat = kit.materials.glass;
  const frameMat = kit.materials.darkWood;
  const doorMat = kit.materials.wood;
  const shutterMat = kit.materials.wood;

  // Floor: top at y=0, thickness 0.025
  kit.box(root, [W, 0.025, D], [0, -0.0125, 0], floorMat);

  // Ceiling: center y=2.85, thickness 0.10
  kit.box(root, [W, 0.10, D], [0, 2.85, 0], ceilMat);

  // Helper to build a wall with an opening
  function buildWallWithOpening(opts) {
    const {
      axis, // 'x' or 'z': axis the wall lies along (length direction)
      center, // [x,y,z] center of wall in its plane
      length, // wall length along axis
      height, // wall height
      thickness, // wall thickness
      openingCenter, // [x,y,z] world (root-local) center of opening
      openingWidth, // along axis
      openingHeight, // along y
      material,
      cutaway
    } = opts;

    const group = new THREE.Group();
    // segments: bottom, top, left, right
    const alongX = axis === 'x';
    const cAlong = alongX ? center[0] : center[2];
    const cAcross = alongX ? center[2] : center[0];
    const oAlong = alongX ? openingCenter[0] : openingCenter[2];
    const oAcross = alongX ? openingCenter[2] : openingCenter[0];
    const oY = openingCenter[1];

    const halfLen = length / 2;
    const halfOpW = openingWidth / 2;
    const halfOpH = openingHeight / 2;

    const leftLen = (oAlong - halfOpW) - (cAlong - halfLen);
    const rightLen = (cAlong + halfLen) - (oAlong + halfOpW);
    const bottomH = (oY - halfOpH) - 0;
    const topH = height - (oY + halfOpH);

    const segs = [];

    // bottom segment: full width, height bottomH, centered at y=bottomH/2
    if (bottomH > 0.001) {
      const size = alongX ? [length, bottomH, thickness] : [thickness, bottomH, length];
      const pos = alongX ? [cAlong, bottomH / 2, cAcross] : [cAcross, bottomH / 2, cAlong];
      segs.push({ size, pos });
    }

    // top segment: full width, height topH, centered at y=oY+halfOpH + topH/2
    if (topH > 0.001) {
      const size = alongX ? [length, topH, thickness] : [thickness, topH, length];
      const yTop = oY + halfOpH + topH / 2;
      const pos = alongX ? [cAlong, yTop, cAcross] : [cAcross, yTop, cAlong];
      segs.push({ size, pos });
    }

    // left segment: from wall start to opening left edge, full height
    if (leftLen > 0.001) {
      const size = alongX ? [leftLen, height, thickness] : [thickness, height, leftLen];
      const xLeft = (cAlong - halfLen) + leftLen / 2;
      const pos = alongX ? [xLeft, height / 2, cAcross] : [cAcross, height / 2, xLeft];
      segs.push({ size, pos });
    }

    // right segment: from opening right edge to wall end, full height
    if (rightLen > 0.001) {
      const size = alongX ? [rightLen, height, thickness] : [thickness, height, rightLen];
      const xRight = (oAlong + halfOpW) + rightLen / 2;
      const pos = alongX ? [xRight, height / 2, cAcross] : [cAcross, height / 2, xRight];
      segs.push({ size, pos });
    }

    for (const s of segs) {
      const mesh = kit.box(group, s.size, s.pos, material);
      if (cutaway) mesh.userData.cutaway = true;
    }
    root.add(group);
    return group;
  }

  // Back wall (z = -2.5) with window opening
  const backWall = buildWallWithOpening({
    axis: 'x',
    center: [0, wallY, -halfD],
    length: W,
    height: wallH,
    thickness: wallT,
    openingCenter: spec.openings.window.center,
    openingWidth: spec.openings.window.size[0],
    openingHeight: spec.openings.window.size[1],
    material: wallMat
  });

  // Right wall (x = 3) with door opening, cutaway
  const rightWall = buildWallWithOpening({
    axis: 'z',
    center: [halfW, wallY, 0],
    length: D,
    height: wallH,
    thickness: wallT,
    openingCenter: spec.openings.door.center,
    openingWidth: spec.openings.door.size[0],
    openingHeight: spec.openings.door.size[1],
    material: wallMat,
    cutaway: true
  });

  // Left wall (x = -3) with vent opening
  const leftWall = buildWallWithOpening({
    axis: 'z',
    center: [-halfW, wallY, 0],
    length: D,
    height: wallH,
    thickness: wallT,
    openingCenter: spec.openings.vent.center,
    openingWidth: spec.openings.vent.size[0],
    openingHeight: spec.openings.vent.size[1],
    material: wallMat
  });

  // Front wall (z = 2.5) solid, cutaway
  const frontWall = new THREE.Group();
  const frontMesh = kit.box(frontWall, [W, wallH, wallT], [0, wallY, halfD], wallMat);
  frontMesh.userData.cutaway = true;
  root.add(frontWall);

  // Window assembly
  const windowGroup = new THREE.Group();
  const winC = spec.openings.window.center;
  const winW = spec.openings.window.size[0];
  const winH = spec.openings.window.size[1];
  const frameDepth = 0.06;
  const frameThick = 0.06;
  const zPos = winC[2] + wallT / 2 + frameDepth / 2; // frame sits just inside back wall

  // four frame bars: top, bottom, left, right
  // top bar
  kit.box(windowGroup, [winW + 2 * frameThick, frameThick, frameDepth], [winC[0], winC[1] + winH / 2 + frameThick / 2, zPos], frameMat);
  // bottom bar
  kit.box(windowGroup, [winW + 2 * frameThick, frameThick, frameDepth], [winC[0], winC[1] - winH / 2 - frameThick / 2, zPos], frameMat);
  // left bar
  kit.box(windowGroup, [frameThick, winH, frameDepth], [winC[0] - winW / 2 - frameThick / 2, winC[1], zPos], frameMat);
  // right bar
  kit.box(windowGroup, [frameThick, winH, frameDepth], [winC[0] + winW / 2 + frameThick / 2, winC[1], zPos], frameMat);

  // glass pane: thin, centered in opening
  kit.box(windowGroup, [winW - 0.04, winH - 0.04, 0.01], [winC[0], winC[1], winC[2] + wallT / 2 + 0.02], glassMat);

  root.add(windowGroup);
  registry.window = windowGroup;

  // Shutters: two wooden panels on inside of window, along back wall, not past z=-2.6
  const shutterGroup = new THREE.Group();
  const shutterW = (winW - 0.02) / 2;
  const shutterH = winH - 0.02;
  const shutterT = 0.03;
  // inside face of back wall is z = -2.5 + 0.04 = -2.46. Place shutters at z = -2.46 - shutterT/2? Actually inside means room side: z > -2.46. But requirement: not past z=-2.6, so z >= -2.6. Put them just inside wall: z = -2.46 + shutterT/2? Let's place them flat against wall inside: z = -2.5 + wallT/2 + shutterT/2 = -2.46 + 0.015 = -2.445. This is > -2.6, good.
  const shutterZ = winC[2] + wallT / 2 + shutterT / 2; // -2.5 + 0.04 + 0.015 = -2.445
  // left shutter
  kit.box(shutterGroup, [shutterW, shutterH, shutterT], [winC[0] - shutterW / 2 - 0.01, winC[1], shutterZ], shutterMat);
  // right shutter
  kit.box(shutterGroup, [shutterW, shutterH, shutterT], [winC[0] + shutterW / 2 + 0.01, winC[1], shutterZ], shutterMat);
  root.add(shutterGroup);
  registry.shutter = shutterGroup;

  // Door assembly (right wall, x=3) cutaway
  const doorGroup = new THREE.Group();
  doorGroup.userData.cutaway = true;
  const doorC = spec.openings.door.center;
  const doorW = spec.openings.door.size[0]; // along Z
  const doorH = spec.openings.door.size[1];
  const doorT = 0.04;
  const frameT = 0.06;
  const xDoor = doorC[0]; // 3
  // Door frame (cutaway) on inside face of right wall: x = 3 - wallT/2 = 2.96
  const frameX = xDoor - wallT / 2 - frameT / 2; // 3 - 0.04 - 0.03 = 2.93
  // top frame bar
  const doorFrameGroup = new THREE.Group();
  doorFrameGroup.userData.cutaway = true;
  kit.box(doorFrameGroup, [frameT, frameT, doorW + 2 * frameT], [frameX, doorC[1] + doorH / 2 + frameT / 2, doorC[2]], frameMat);
  // bottom frame bar
  kit.box(doorFrameGroup, [frameT, frameT, doorW + 2 * frameT], [frameX, doorC[1] - doorH / 2 - frameT / 2, doorC[2]], frameMat);
  // left frame bar (along Z start)
  kit.box(doorFrameGroup, [frameT, doorH, frameT], [frameX, doorC[1], doorC[2] - doorW / 2 - frameT / 2], frameMat);
  // right frame bar
  kit.box(doorFrameGroup, [frameT, doorH, frameT], [frameX, doorC[1], doorC[2] + doorW / 2 + frameT / 2], frameMat);
  root.add(doorFrameGroup);

  // Door panel: closed, lies in YZ plane (no rotation along Z), at x=3? Actually door panel should be at x=3 (center of wall). Place at x = 3, thickness along X.
  const doorPanel = kit.box(doorGroup, [doorT, doorH, doorW - 0.02], [xDoor, doorC[1], doorC[2]], doorMat);
  doorPanel.userData.cutaway = true;
  root.add(doorGroup);
  registry.door = doorGroup;

  // Vent assembly (left wall, x=-3)
  const ventGroup = new THREE.Group();
  const ventC = spec.openings.vent.center;
  const ventW = spec.openings.vent.size[0]; // along Z
  const ventH = spec.openings.vent.size[1];
  const vFrameT = 0.02;
  const vDepth = 0.04;
  const xVent = ventC[0]; // -3
  // frame on inside face: x = -3 + wallT/2 = -2.96
  const vFrameX = xVent + wallT / 2 + vDepth / 2; // -3 + 0.04 + 0.02 = -2.94
  // four narrow bars
  // top
  kit.box(ventGroup, [vDepth, vFrameT, ventW + 2 * vFrameT], [vFrameX, ventC[1] + ventH / 2 + vFrameT / 2, ventC[2]], frameMat);
  // bottom
  kit.box(ventGroup, [vDepth, vFrameT, ventW + 2 * vFrameT], [vFrameX, ventC[1] - ventH / 2 - vFrameT / 2, ventC[2]], frameMat);
  // left
  kit.box(ventGroup, [vDepth, ventH, vFrameT], [vFrameX, ventC[1], ventC[2] - ventW / 2 - vFrameT / 2], frameMat);
  // right
  kit.box(ventGroup, [vDepth, ventH, vFrameT], [vFrameX, ventC[1], ventC[2] + ventW / 2 + vFrameT / 2], frameMat);
  // sparse grille: three thin horizontal bars inside opening
  const grilleMat = kit.materials.metal;
  const grilleT = 0.01;
  for (let i = 0; i < 3; i++) {
    const y = ventC[1] - ventH / 2 + (i + 1) * ventH / 4;
    kit.box(ventGroup, [0.01, grilleT, ventW - 0.02], [vFrameX, y, ventC[2]], grilleMat);
  }
  root.add(ventGroup);
  registry.vent = ventGroup;

  // Baseboard trim (wainscoting) only below y=0.8, not crossing walls or blocking openings
  const trimGroup = new THREE.Group();
  const trimH = 0.15;
  const trimT = 0.02;
  const trimY = trimH / 2;
  // Back wall trim: from x start to window left, then window right to end
  const winLeft = winC[0] - winW / 2;
  const winRight = winC[0] + winW / 2;
  const backZ = halfD - wallT / 2 - trimT / 2; // inside face: z = 2.5 - 0.04 - 0.01 = 2.45
  // left part
  if (winLeft > -halfW) kit.box(trimGroup, [winLeft - (-halfW), trimH, trimT], [(-halfW + winLeft) / 2, trimY, backZ], trimMat);
  // right part
  if (winRight < halfW) kit.box(trimGroup, [halfW - winRight, trimH, trimT], [(winRight + halfW) / 2, trimY, backZ], trimMat);

  // Front wall trim: full width, inside face z = 2.5 - 0.04 - 0.01 = 2.45? Wait front wall is at z=+2.5, inside is z=2.5-0.04=2.46, trim at 2.46-0.01=2.45
  kit.box(trimGroup, [W - 2 * trimT, trimH, trimT], [0, trimY, halfD - wallT / 2 - trimT / 2], trimMat);

  // Left wall trim: full depth, inside face x = -3 + 0.04 = -2.96, trim at -2.96 + 0.01 = -2.95
  kit.box(trimGroup, [trimT, trimH, D - 2 * trimT], [-halfW + wallT / 2 + trimT / 2, trimY, 0], trimMat);

  // Right wall trim: split around door, door spans z from doorC[2]-doorW/2 to doorC[2]+doorW/2
  const doorLeft = doorC[2] - doorW / 2;
  const doorRight = doorC[2] + doorW / 2;
  const rightInnerX = halfW - wallT / 2 - trimT / 2; // 3 - 0.04 - 0.01 = 2.95
  if (doorLeft > -halfD) kit.box(trimGroup, [trimT, trimH, doorLeft - (-halfD)], [rightInnerX, trimY, (-halfD + doorLeft) / 2], trimMat);
  if (doorRight < halfD) kit.box(trimGroup, [trimT, trimH, halfD - doorRight], [rightInnerX, trimY, (doorRight + halfD) / 2], trimMat);

  root.add(trimGroup);

  // Ensure root transform is identity
  root.position.set(0, 0, 0);
  root.rotation.set(0, 0, 0);
  root.scale.set(1, 1, 1);

  return {
    root,
    bindings: {
      window: registry.window,
      shutter: registry.shutter,
      door: registry.door,
      vent: registry.vent
    },
    sockets: {},
    motions: {}
  };
}