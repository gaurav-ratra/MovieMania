// SiteWrap admin panel. No dependencies: `node server.js`  (Node 18+)
// Anyone can READ the list (the app needs that); WRITING needs the admin password.
const http = require('http'), fs = require('fs'), path = require('path'), crypto = require('crypto');

const PORT = process.env.PORT || 8080;
const DATA = path.join(__dirname, 'data');
const FILE = path.join(DATA, 'sites.json');
const PWFILE = path.join(DATA, 'password.txt');
fs.mkdirSync(DATA, { recursive: true });

// password: env var, else the saved one, else generate and save one
let PASSWORD = process.env.ADMIN_PASSWORD;
if (!PASSWORD) {
  if (!fs.existsSync(PWFILE)) fs.writeFileSync(PWFILE, crypto.randomBytes(6).toString('hex'));
  PASSWORD = fs.readFileSync(PWFILE, 'utf8').trim();
}
if (!fs.existsSync(FILE)) fs.writeFileSync(FILE, JSON.stringify([
  { name: 'Wikipedia', url: 'https://www.wikipedia.org', mode: 0, cursor: false },
  { name: 'YouTube TV UI', url: 'https://www.youtube.com/tv', mode: 2, cursor: true },
], null, 2));

const same = (a, b) => a.length === b.length && crypto.timingSafeEqual(Buffer.from(a), Buffer.from(b));

// Validate + normalise whatever the browser sends; never trust it.
function clean(list) {
  if (!Array.isArray(list) || list.length > 500) throw new Error('bad list');
  return list.map(s => {
    let url = String(s.url || '').trim();
    if (!url) throw new Error('empty url');
    if (!url.includes('://')) url = 'https://' + url;
    if (!/^https?:\/\//i.test(url)) throw new Error('only http(s) urls');
    return {
      name: String(s.name || '').trim().slice(0, 60) || new URL(url).hostname,
      url,
      mode: [0, 1, 2].includes(+s.mode) ? +s.mode : 0,
      cursor: !!s.cursor,
    };
  });
}

http.createServer((req, res) => {
  const send = (code, body, type = 'application/json') => { res.writeHead(code, { 'Content-Type': type }); res.end(body); };
  const url = req.url.split('?')[0];

  if (req.method === 'GET' && (url === '/' || url === '/index.html'))
    return send(200, fs.readFileSync(path.join(__dirname, 'admin.html')), 'text/html; charset=utf-8');
  if (req.method === 'GET' && url === '/api/sites') return send(200, fs.readFileSync(FILE));
  if (req.method === 'PUT' && url === '/api/sites') {
    if (!same(String(req.headers['x-admin-password'] || ''), PASSWORD)) return send(401, '{"error":"wrong password"}');
    let body = '';
    req.on('data', d => { body += d; if (body.length > 200000) req.destroy(); });
    req.on('end', () => {
      try { fs.writeFileSync(FILE, JSON.stringify(clean(JSON.parse(body)), null, 2)); send(200, '{"ok":true}'); }
      catch (e) { send(400, JSON.stringify({ error: e.message })); }
    });
    return;
  }
  send(404, '{"error":"not found"}');
}).listen(PORT, () => {
  console.log(`SiteWrap admin on http://localhost:${PORT}   (admin password: ${PASSWORD})`);
});
