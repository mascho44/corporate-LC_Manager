/* Poststelle: simple page for a mailroom to deliver scans with an access token (no login). Everything is sent to POST /api/capture/upload, one file per call. */
(() => {
 const messages = () => ({ 401: tx('ps.e401', 'Der Zugang ist ungültig oder wurde widerrufen. Bitte bei der Administration einen neuen anfordern.'), 403: tx('ps.e403', 'Dieser Zugang darf das nicht.'), 413: tx('ps.e413', 'Die Datei ist zu groß.') });
 /** Result line for a receipt of the server or an error: { kind: ok|warn|fail, text }. */
 function describe(receipt, error) {
  if (error) return { kind: 'fail', text: error };
  if (receipt.status === 'DUPLICATE') return { kind: 'warn', text: tx('ps.duplicate', 'Schon früher angeliefert – nicht ein zweites Mal angelegt.') };
  if (receipt.pageCountMatches === false) return { kind: 'warn', text: tx('ps.pages', 'Angeliefert, aber die Seitenzahl weicht ab (erkannt {n}). Bitte den Stapel prüfen.', { n: receipt.actualPages ?? '?' }) };
  return { kind: 'ok', text: tx('ps.delivered', 'Angeliefert.') };
 }
 function httpMessage(status, body) {
  return messages()[status] || (body && body.error) || tx('ps.error', 'Fehler {status}. Bitte noch einmal versuchen.', { status });
 }
 function defaultBatch(date) {
  const p = n => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}`;
 }
 async function sha256Hex(file) {
  if (!globalThis.crypto?.subtle) return null;
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('');
 }
 globalThis.LcPoststelle = { describe, httpMessage, defaultBatch };
 if (typeof document === 'undefined' || !document.body || !document.querySelector('#drop')) return;

 const $ = id => document.getElementById(id);
 const store = {
  get(key) { try { return localStorage.getItem(key) || ''; } catch { return ''; } },
  set(key, value) { try { value ? localStorage.setItem(key, value) : localStorage.removeItem(key); } catch { /* storage may be blocked */ } }
 };
 $('token').value = store.get('ps.token'); $('scanner').value = store.get('ps.scanner'); $('remember').checked = !!store.get('ps.token');
 $('batch').value = store.get('ps.batch') || defaultBatch(new Date());
 const remember = () => { store.set('ps.token', $('remember').checked ? $('token').value.trim() : ''); store.set('ps.scanner', $('scanner').value.trim()); store.set('ps.batch', $('batch').value.trim()); };
 ['token', 'scanner', 'batch'].forEach(id => $(id).addEventListener('change', remember)); $('remember').addEventListener('change', remember);

 let ok = 0, warn = 0, fail = 0;
 const summary = () => { $('summary').textContent = ok + warn + fail ? tx('ps.summary', '{ok} angeliefert{warn}{fail}.', { ok, warn: warn ? tx('ps.summaryWarn', ', {n} mit Hinweis', { n: warn }) : '', fail: fail ? tx('ps.summaryFail', ', {n} fehlgeschlagen', { n: fail }) : '' }) : ''; };
 function line(file) {
  const li = document.createElement('li'), mark = document.createElement('b'), name = document.createElement('span'), note = document.createElement('small');
  mark.textContent = '…'; name.textContent = file.name; note.textContent = tx('ps.sending', 'wird gesendet'); li.append(mark, name, note); $('results').prepend(li);
  return result => { li.className = result.kind; mark.textContent = { ok: '✓', warn: '⚠', fail: '✗' }[result.kind]; note.textContent = result.text; };
 }
 async function send(file, token) {
  const done = line(file);
  try {
   const form = new FormData(); form.append('file', file);
   if ($('batch').value.trim()) form.append('batchId', $('batch').value.trim());
   if ($('scanner').value.trim()) form.append('scanner', $('scanner').value.trim());
   const hash = await sha256Hex(file); if (hash) form.append('sha256', hash);
   const response = await fetch('/api/capture/upload', { method: 'POST', headers: { Authorization: 'Bearer ' + token }, body: form });
   const body = await response.json().catch(() => null);
   if (!response.ok) { fail++; done(describe(null, httpMessage(response.status, body))); return response.status === 401; }
   const result = describe(body[0] || {}); result.kind === 'ok' ? ok++ : warn++; done(result);
  } catch { fail++; done(describe(null, tx('ps.noConnection', 'Keine Verbindung zum Server. Bitte noch einmal versuchen.'))); }
  return false;
 }
 async function deliver(files) {
  const token = $('token').value.trim();
  if (!token.startsWith('lcc_')) { $('summary').textContent = tx('ps.needToken', 'Bitte zuerst den Erfassungszugang eintragen (beginnt mit lcc_).'); $('token').focus(); return; }
  remember();
  for (const file of files) { if (await send(file, token)) break; }
  summary();
 }
 const pick = () => $('files').click();
 $('drop').addEventListener('click', pick);
 $('drop').addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); pick(); } });
 $('files').addEventListener('change', () => { const files = [...$('files').files]; $('files').value = ''; deliver(files); });
 ['dragenter', 'dragover'].forEach(type => $('drop').addEventListener(type, event => { event.preventDefault(); $('drop').classList.add('over'); }));
 ['dragleave', 'drop'].forEach(type => $('drop').addEventListener(type, event => { event.preventDefault(); $('drop').classList.remove('over'); }));
 $('drop').addEventListener('drop', event => deliver([...event.dataTransfer.files]));
})();
