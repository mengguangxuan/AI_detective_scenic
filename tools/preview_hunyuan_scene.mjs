// Local, read-only artifact viewer. Does not expose the model cache, keys or provider URLs.
import http from 'node:http';
import { createReadStream, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('../', import.meta.url));
const asset = path.join(root, 'frontend/.build-cache/hunyuan-preview');
const three = path.join(root, 'frontend/node_modules/three');
const files = new Map([
  ['/', [path.join(root,'tools/hunyuan-preview/index.html'),'text/html; charset=utf-8']],
  ['/viewer.js', [path.join(root,'tools/hunyuan-preview/viewer.js'),'text/javascript']],
  ['/spark.module.js', [path.join(asset,'spark.module.js'),'text/javascript']],
  ['/scene.spz', [path.join(asset,'scene.spz'),'application/octet-stream']],
  ['/panorama.png', [path.join(asset,'panorama.png'),'image/png']],
  ['/position.json', [path.join(asset,'position.json'),'application/json']],
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
  res.writeHead(200, {'Content-Type':entry[1], 'Cache-Control':'no-cache','X-Content-Type-Options':'nosniff'});
  if (req.method === 'HEAD') { res.end(); return; }
  const stream = createReadStream(entry[0]);
  stream.on('error', () => res.destroy());
  stream.pipe(res);
}).listen(5176, '127.0.0.1', () => console.log('Hunyuan artifact preview: http://127.0.0.1:5176/'));
