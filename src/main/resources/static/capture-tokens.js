/* Settings box "Erfassungszugänge": tokens for scan stations (mailroom) that may only deliver documents to the inbox of this tenant. */
(() => {
 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 const stamp = value => value ? new Date(value).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' }) : '–';
 function rowText(token) {
  const state = token.revoked ? tx('ctx.revoked','widerrufen') : token.lastUsedAt ? tx('ctx.lastUsed','zuletzt benutzt {when}',{when:stamp(token.lastUsedAt)}) : tx('ctx.never','noch nie benutzt');
  const quiet = token.quietWorkingDays ? ` · ⚠ ruhig seit ${quietText(token.quietWorkingDays)}` : '';
  return `${token.name} · ${token.prefix}… · angelegt ${stamp(token.createdAt)}${token.createdBy ? tx('ctl.c8a06e',' von ') + token.createdBy : ''} · ${state}${quiet}`;
 }
 const quietText = days => `${days} Arbeitstag${days === 1 ? '' : 'en'}`;
 /** Warning line for the inbox: stations that delivered nothing for a while (null when all is well). */
 function silentWarning(silent) {
  if (!silent?.length) return null;
  const parts = silent.map(s => `${s.name} (${s.neverUsed ? 'noch nie benutzt, seit ' : 'seit '}${quietText(s.quietWorkingDays)})`);
  return tx('ctt.91b870','Keine Lieferung von: {p0}. Bitte in der Poststelle nachfragen, ob dort alles läuft.',{p0:parts.join(', ')});
 }
 globalThis.LcCaptureTokens = { rowText, silentWarning };
 if (typeof document === 'undefined' || !document.body) return;

 /** Shows the warning at the top of the inbox; checked every few minutes while the inbox is open. */
 let lastWarning = null;
 async function checkSilent() {
  const section = document.querySelector('#inboxSection');
  if (!section || section.classList.contains('hidden')) return;
  let text = null;
  try { text = silentWarning(await json('/api/capture-tokens/silent')); } catch (error) { return; }
  let note = section.querySelector('.capture-silent');
  if (!text) { note?.remove(); lastWarning = null; return; }
  if (!note) { note = node('p', '', 'capture-silent'); note.setAttribute('role', 'status'); section.prepend(note); }
  if (text !== lastWarning) { note.textContent = '⚠ ' + text; lastWarning = text; }
 }
 setInterval(checkSilent, 300000); setTimeout(checkSilent, 3000);

 async function build(host) {
  const box = node('section', undefined, 'settings-box'); box.id = 'captureTokenBox';
  const message = node('p', '', 'settings-status'); message.setAttribute('role', 'status');
  const list = node('div', undefined, 'settings-list'); const secret = node('div', undefined, 'capture-secret'); secret.hidden = true;
  const name = document.createElement('input'); name.maxLength = 100; name.placeholder = 'z. B. Poststelle Scanner 2'; name.setAttribute('aria-label', tx('ctl.d1fc53','Name des Zugangs'));
  const create = node('button', tx('ctl.af58cb','Zugang anlegen')); create.type = 'button';
  const row = node('div', undefined, 'settings-inline'); row.append(name, create);
  box.append(node('h3', tx('ctl.4704f6','Erfassungszugänge')),
   node('p', tx('ctl.8c48d4','Ein Zugang erlaubt einer Scan-Station, Dokumente in den Posteingang dieses Mandanten zu laden. Er kann nichts anderes: weder Akten lesen noch Prüfungen ändern. Das Geheimnis wird nur einmal angezeigt.')),
   row, secret, message, list);
  host.append(box);
  const render = tokens => {
   list.replaceChildren();
   if (!tokens.length) list.append(node('p', tx('ctl.8c577b','Noch kein Zugang angelegt.')));
   tokens.forEach(token => {
    const item = node('div', undefined, 'settings-row'); item.append(node('span', rowText(token)));
    if (!token.revoked) {
     const revoke = node('button', tx('ctl.4d2063','Widerrufen'), 'secondary'); revoke.type = 'button';
     revoke.onclick = async () => {
      if (typeof confirmAction === 'function' && !await confirmAction(tx('ctt.afdcb8','Zugang „{p0}" widerrufen? Die Scan-Station kann danach keine Dokumente mehr senden.',{p0:token.name}))) return;
      try { await json(`/api/capture-tokens/${encodeURIComponent(token.id)}`, { method: 'DELETE' }); render(await json('/api/capture-tokens')); message.textContent = tx('ctl.bdca21','Zugang widerrufen.'); }
      catch (error) { message.textContent = error.message; }
     };
     item.append(revoke);
    }
    list.append(item);
   });
  };
  create.onclick = async () => {
   create.disabled = true; message.textContent = '';
   try {
    const created = await json('/api/capture-tokens', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: name.value }) });
    secret.hidden = false; secret.replaceChildren(node('b', tx('ctl.7b01bf','Zugang „') + created.token.name + tx('ctl.75f768','" angelegt. Bitte jetzt kopieren; später ist er nicht mehr sichtbar:')));
    const code = node('code', created.secret); code.style.display = 'block'; code.style.wordBreak = 'break-all'; secret.append(code);
    name.value = '';
    render(await json('/api/capture-tokens'));
   } catch (error) { message.textContent = error.message; }
   finally { create.disabled = false; }
  };
  try { render(await json('/api/capture-tokens')); } catch (error) { message.textContent = error.message; }
 }
 const wait = setInterval(() => {
  const host = document.querySelector('#tenantSection');
  if (host && typeof can === 'function' && typeof currentUser !== 'undefined' && currentUser) {
   clearInterval(wait);
   if (can('SETTINGS_MANAGE') && !host.querySelector('#captureTokenBox')) build(host);
  }
 }, 500);
})();
