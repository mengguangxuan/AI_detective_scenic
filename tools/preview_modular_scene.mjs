// Local, read-only artifact viewer. Does not expose the model cache, keys or provider URLs.
import http from 'node:http';
import { createReadStream, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('../', import.meta.url));
const asset = path.join(root, 'frontend/.build-cache/modular-scene');
const three = path.join(root, 'frontend/node_modules/three');
const files = new Map([
  ['/', [path.join(root,'tools/modular-scene/index.html'),'text/html; charset=utf-8']],
  ['/viewer.js', [path.join(root,'tools/modular-scene/viewer.js'),'text/javascript']],
  ['/kit.js', [path.join(root,'tools/modular-scene/kit.js'),'text/javascript']],
  ['/inspect-output.js', [path.join(root,'tools/modular-scene/inspect-output.js'),'text/javascript']],
  ['/source/contract.txt', [path.join(root,'tools/modular-scene/contract.md'),'text/plain; charset=utf-8']],
  ['/source/assemble.js.txt', [path.join(root,'tools/modular-scene/assemble.js'),'text/plain; charset=utf-8']],
  ...['shell','bed','cabinet','furnishings'].map(name=>['/source/'+name+'.js.txt',[path.join(asset,name+'.candidate.js'),'text/plain; charset=utf-8']]),
  ['/source/initial-bed.js.txt', [path.join(asset,'bed.initial.js'),'text/plain; charset=utf-8']],
  ['/source/checks.json', [path.join(asset,'preflight.json'),'application/json']],
  ['/assemble.js', [path.join(root,'tools/modular-scene/assemble.js'),'text/javascript']],
  ['/layout.json', [path.join(root,'tools/modular-scene/layout.json'),'application/json']],
  ['/review.json', [path.join(asset,'review.json'),'application/json']],
  ...['shell','bed','cabinet','furnishings'].map(name=>['/modules/'+name+'.js',[path.join(asset,name+'.candidate.js'),'text/javascript']]),
  ['/three.module.js', [path.join(three,'build/three.module.js'),'text/javascript']],
  ['/three.core.js', [path.join(three,'build/three.core.js'),'text/javascript']],
  ['/addons/controls/OrbitControls.js', [path.join(three,'examples/jsm/controls/OrbitControls.js'),'text/javascript']],
  ['/addons/postprocessing/Pass.js', [path.join(three,'examples/jsm/postprocessing/Pass.js'),'text/javascript']],
]);
for (const [file] of files.values()) if (!existsSync(file)) throw new Error('Preview asset missing: '+path.basename(file));
http.createServer((req,res) => {
  if (!['GET','HEAD'].includes(req.method)) { res.writeHead(405).end(); return; }
  const entry = files.get(new URL(req.url, "http://127.0.0.1").pathname);
  if (!entry) { res.writeHead(404).end(); return; }
  res.writeHead(200, {'Content-Type':entry[1], 'Cache-Control':'no-cache','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'"});
  if (req.method === 'HEAD') { res.end(); return; }
  const stream = createReadStream(entry[0]);
  stream.on('error', () => res.destroy());
  stream.pipe(res);
}).listen(5178, '127.0.0.1', () => console.log('Modular scene preview: http://127.0.0.1:5178/'));
