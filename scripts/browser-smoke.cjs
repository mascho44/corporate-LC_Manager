// Disposable localhost installation ONLY (scripts/test-browser-smoke.sh). Opens the pages in headless Chrome and fails on uncaught
// JavaScript errors, e.g. a function that a page uses but does not load. No dependencies: Chrome is driven over the DevTools protocol.
const assert = require('node:assert/strict'), crypto = require('node:crypto'), fs = require('node:fs'), os = require('node:os'), path = require('node:path'), { spawn } = require('node:child_process');
const base = 'http://127.0.0.1:18086', cookies = new Map(); let csrf = '';
async function call(p, method = 'GET', body) {
 const headers = { Cookie: [...cookies].map(([k, v]) => `${k}=${v}`).join(';') }; if (csrf) headers['X-CSRF-TOKEN'] = csrf; if (body !== undefined) headers['Content-Type'] = 'application/json';
 const response = await fetch(base + p, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
 for (const cookie of response.headers.getSetCookie()) { const pair = cookie.split(';')[0], at = pair.indexOf('='); cookies.set(pair.slice(0, at), pair.slice(at + 1)); }
 const text = await response.text(); assert.ok(response.ok, `${method} ${p}: HTTP ${response.status}`); return text ? JSON.parse(text) : null;
}
function totp(secret) {
 let buffer = 0, bits = 0; const bytes = []; for (const c of secret) { buffer = (buffer << 5) | 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(c); bits += 5; if (bits >= 8) { bytes.push((buffer >>> (bits - 8)) & 255); bits -= 8; } }
 const counter = Buffer.alloc(8); counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30000))); const h = crypto.createHmac('sha1', Buffer.from(bytes)).update(counter).digest(), offset = h[19] & 15; return String((h.readUInt32BE(offset) & 0x7fffffff) % 1000000).padStart(6, '0');
}
function chromePath() {
 const candidates = [process.env.CHROME_BIN, '/usr/bin/google-chrome', '/usr/bin/google-chrome-stable', '/usr/bin/chromium', '/usr/bin/chromium-browser', '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'].filter(Boolean);
 const found = candidates.find(c => fs.existsSync(c)); assert.ok(found, 'No Chrome found (set CHROME_BIN)'); return found;
}
async function devtools(port) {
 for (let i = 0; i < 50; i++) { try { return (await (await fetch(`http://127.0.0.1:${port}/json/version`)).json()).webSocketDebuggerUrl; } catch { await new Promise(r => setTimeout(r, 200)); } }
 throw new Error('Chrome did not start');
}
function client(url) {
 const ws = new WebSocket(url); let id = 0; const pending = new Map(), listeners = [];
 ws.onmessage = event => { const m = JSON.parse(event.data); if (m.id && pending.has(m.id)) { const { resolve, reject } = pending.get(m.id); pending.delete(m.id); m.error ? reject(new Error(m.error.message)) : resolve(m.result); } else listeners.forEach(l => l(m)); };
 const ready = new Promise(r => ws.onopen = r);
 return { ready, on: l => listeners.push(l), send: (method, params = {}, sessionId) => new Promise((resolve, reject) => { const m = { id: ++id, method, params }; if (sessionId) m.sessionId = sessionId; pending.set(m.id, { resolve, reject }); ws.send(JSON.stringify(m)); }), close: () => ws.close() };
}
(async () => {
 const setup = await call('/api/auth/login', 'POST', { username: 'admin', password: 'SyntheticOnly123!' });
 const secret = setup.secret; await call('/api/auth/login/totp', 'POST', { code: totp(secret) }); csrf = (await call('/api/auth/me')).csrfToken;
 const port = 9300 + Math.floor(Math.random() * 500), profile = fs.mkdtempSync(path.join(os.tmpdir(), 'lc-smoke-'));
 const chrome = spawn(chromePath(), ['--headless=new', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, '--no-first-run', '--no-sandbox', '--disable-gpu', 'about:blank'], { stdio: 'ignore' });
 const failures = [];
 try {
  const cdp = client(await devtools(port)); await cdp.ready;
  const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
  const send = (m, p) => cdp.send(m, p, sessionId);
  let current = '';
  cdp.on(m => { if (m.sessionId === sessionId && m.method === 'Runtime.exceptionThrown') { const d = m.params.exceptionDetails; failures.push(`${current}: ${d.exception?.description?.split('\n')[0] || d.text} (${d.url || ''}:${d.lineNumber})`); } });
  await send('Runtime.enable'); await send('Page.enable'); await send('Network.enable');
  for (const [name, value] of cookies) await send('Network.setCookie', { name, value, url: base });
  const visit = async page => { current = page; await send('Page.navigate', { url: base + page }); await new Promise(r => setTimeout(r, 3500)); };
  for (const language of ['de', 'en']) {
   await call('/api/profile/language', 'PUT', { language });
   for (const page of ['/', '/platform.html', '/info.html', '/rule-packs.html']) await visit(page);
  }
  await send('Network.clearBrowserCookies');
  for (const page of ['/login.html', '/platform-login.html', '/password-reset.html', '/invitation.html', '/poststelle.html']) await visit(page);
  cdp.close();
 } finally { chrome.kill(); }
 if (failures.length) { console.error('JavaScript errors:\n' + failures.join('\n')); process.exit(1); }
 console.log('Browser smoke test: no uncaught JavaScript errors on the checked pages.');
})().catch(error => { console.error(error); process.exit(1); });
