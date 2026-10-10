// Local CI-only HTTPS origin for the built Angular app and the real backend.
import https from 'node:https';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve('frontend/dist/frontend/browser');
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon' };
const server = https.createServer({
  key: fs.readFileSync(process.env.CI_TLS_KEY),
  cert: fs.readFileSync(process.env.CI_TLS_CERT),
}, (request, response) => {
  let pathname;
  try { pathname = decodeURIComponent(new URL(request.url, 'https://localhost').pathname); }
  catch { response.writeHead(400).end(); return; }
  if (pathname.startsWith('/api/')) {
    const upstream = http.request({ hostname: '127.0.0.1', port: 8080, path: request.url, method: request.method, headers: request.headers }, result => {
      response.writeHead(result.statusCode, result.headers);
      result.pipe(response);
    });
    upstream.on('error', () => response.writeHead(502).end());
    request.pipe(upstream);
    return;
  }
  const file = path.resolve(root, '.' + pathname);
  if (file !== root && !file.startsWith(root + path.sep)) { response.writeHead(403).end(); return; }
  const candidate = file === root ? path.join(root, 'index.html') : file;
  if (!fs.existsSync(candidate) || !fs.statSync(candidate).isFile()) { response.writeHead(404).end(); return; }
  response.writeHead(200, { 'Content-Type': types[path.extname(candidate)] ?? 'application/octet-stream', 'Cache-Control': 'no-store' });
  fs.createReadStream(candidate).pipe(response);
});
server.listen(8443, '127.0.0.1');
