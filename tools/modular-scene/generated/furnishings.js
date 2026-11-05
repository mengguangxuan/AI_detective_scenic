export function buildAsset(THREE, spec, kit) {
  const g = new THREE.Group();
  const b = {};
  const s = {};
  const M = kit.materials;
  const mk = (p, sz, pos, m) => kit.box(p, sz, pos, m);
  const [W,H,D] = spec.size;

  if (spec.kind === 'chair') {
    const leg = Math.min(0.055, W/9), ls = W/2 - leg/2 - 0.01, ds = D/2 - leg/2 - 0.01;
    const seatY = H * 0.45, seatT = 0.05;
    [[-ls,-ds],[ls,-ds],[-ls,ds],[ls,ds]].forEach(([x,z]) => mk(g, [leg, seatY, leg], [x, seatY/2, z], M.wood));
    mk(g, [W, seatT, D], [0, seatY + seatT/2, 0], M.wood);
    const legTop = seatY + seatT, bh = H - legTop;
    [[-ls,ds],[ls,ds]].forEach(([x,z]) => mk(g, [leg, bh, leg], [x, legTop + bh/2, z], M.wood));
    const n = 5;
    for (let i=0;i<n;i++) {
      const t = (i+1)/(n+1);
      const y = legTop + 0.06 + t * Math.max(0.05, bh-0.14);
      mk(g, [W-0.07, 0.032, 0.04], [0, y, D/2 - 0.035 - t*0.05], M.wicker);
    }
    [[-1],[1]].forEach(([sg]) => mk(g, [0.045, 0.045, D-0.09], [sg*(W/2-0.025), seatY+0.16, 0.01], M.wood));
    b.chair = g;
  }

  if (spec.kind === 'dressing_table') {
    const topY = H * 0.52, tw = W, td = D * 0.72, topT = 0.045;
    const leg = Math.max(0.045, W/20);
    mk(g, [tw, topT, td], [0, topY - topT/2, 0], M.wood);
    [[-1,-1],[1,-1],[-1,1],[1,1]].forEach(([sx,sz]) => mk(g, [leg, topY - topT, leg], [sx*(tw/2 - leg/2 - 0.01), (topY-topT)/2, sz*(td/2 - leg/2 - 0.01)], M.wood));
    const dwW = tw * 0.6, dhH = 0.12, ddD = td * 0.7;
    const dY = topY - topT - dhH/2, dZ = -td * 0.03;
    mk(g, [dwW, dhH, ddD], [0, dY, dZ], M.wood);
    mk(g, [dwW*0.88, 0.012, 0.01], [0, dY, dZ - ddD/2 - 0.004], M.brass);
    const my = topY + 0.3, mw = tw * 0.7;
    const mh = Math.max(0, H - 0.12 - my);
    if (mh > 0.22) {
      const rw = mw/2, rh = mh/2, cy = my + rh;
      const pts = [];
      for (let i=0;i<=28;i++) { const a = i/28*Math.PI*2; pts.push([Math.cos(a)*rw, cy + Math.sin(a)*rh, 0]); }
      kit.tube(g, pts, 0.022, M.darkWood);
      const mz = 0.018;
      mk(g, [mw*0.85, mh*0.85, 0.012], [0, cy, mz], M.mirror);
      const supH = my - topY;
      if (supH > 0.02) mk(g, [0.04, supH, 0.04], [0, topY + supH/2, 0], M.darkWood);
    }
    b.dressing_table = g;
  }

  if (spec.kind === 'fireplace') {
    const hw = W/2, hh = H, d = D;
    const pil = Math.max(0.08, W * 0.13);
    const beamH = Math.max(0.14, H * 0.16);
    mk(g, [pil, hh, d], [-hw + pil/2, hh/2, 0], M.stone);
    mk(g, [pil, hh, d], [hw - pil/2, hh/2, 0], M.stone);
    mk(g, [W, beamH, d], [0, hh - beamH/2, 0], M.darkWood);
    const lint = 0.05;
    const lintY = hh - beamH - lint/2;
    mk(g, [W, lint, d], [0, lintY, 0], M.stone);
    const il = hw - pil, ih = hh - beamH - lint, id = d * 0.55;
    if (il > 0.02 && ih > 0.02) {
      mk(g, [il, ih, id], [0, ih/2, -d/2 + id/2 + 0.01], M.soot);
      mk(g, [il*0.7, ih*0.5, 0.02], [0, ih*0.3, -d/2 + id + 0.018], M.soot);
    }
    mk(g, [W, 0.05, d], [0, 0.025, 0], M.stone);
    b.fireplace = g;
  }

  if (spec.kind === 'rug') {
    const th = Math.max(0.008, Math.min(H, 0.03));
    mk(g, [W, th, D], [0, th/2, 0], M.rug);
    const bd = Math.min(W, D) * 0.06, bt = Math.min(0.006, th/3);
    const edgeY = th/2;
    [[0, D/2 - bd/2], [0, -D/2 + bd/2]].forEach(([x,z]) => mk(g, [W, bt, bd], [x, edgeY, z], M.brass));
    [[W/2 - bd/2, 0], [-W/2 + bd/2, 0]].forEach(([x,z]) => mk(g, [bd, bt, D - 2*bd], [x, edgeY, z], M.brass));
    b.rug = g;
  }

  s[spec.kind] = [0, 0, 0];
  return { root: g, bindings: b, sockets: s, motions: {} };
}
