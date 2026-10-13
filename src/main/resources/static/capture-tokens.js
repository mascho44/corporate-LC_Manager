/* Settings box "Erfassungszugänge": tokens for scan stations (mailroom) that may only deliver documents to the inbox of this tenant. */
(() => {
 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 const stamp = value => value ? new Date(value).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' }) : '–';
 function rowText(token) {
  const state = token.revoked ? 'widerrufen' : token.lastUsedAt ? 'zuletzt benutzt ' + stamp(token.lastUsedAt) : 'noch nie benutzt';
  return `${token.name} · ${token.prefix}… · angelegt ${stamp(token.createdAt)}${token.createdBy ? ' von ' + token.createdBy : ''} · ${state}`;
 }
 globalThis.LcCaptureTokens = { rowText };
 if (typeof document === 'undefined' || !document.body) return;

 async function build(host) {
  const box = node('section', undefined, 'settings-box'); box.id = 'captureTokenBox';
  const message = node('p', '', 'settings-status'); message.setAttribute('role', 'status');
  const list = node('div', undefined, 'settings-list'); const secret = node('div', undefined, 'capture-secret'); secret.hidden = true;
  const name = document.createElement('input'); name.maxLength = 100; name.placeholder = 'z. B. Poststelle Scanner 2'; name.setAttribute('aria-label', 'Name des Zugangs');
  const create = node('button', 'Zugang anlegen'); create.type = 'button';
  const row = node('div', undefined, 'settings-inline'); row.append(name, create);
  box.append(node('h3', 'Erfassungszugänge'),
   node('p', 'Ein Zugang erlaubt einer Scan-Station, Dokumente in den Posteingang dieses Mandanten zu laden. Er kann nichts anderes: weder Akten lesen noch Prüfungen ändern. Das Geheimnis wird nur einmal angezeigt.'),
   row, secret, message, list);
  host.append(box);
  const render = tokens => {
   list.replaceChildren();
   if (!tokens.length) list.append(node('p', 'Noch kein Zugang angelegt.'));
   tokens.forEach(token => {
    const item = node('div', undefined, 'settings-row'); item.append(node('span', rowText(token)));
    if (!token.revoked) {
     const revoke = node('button', 'Widerrufen', 'secondary'); revoke.type = 'button';
     revoke.onclick = async () => {
      if (typeof confirmAction === 'function' && !await confirmAction(`Zugang „${token.name}" widerrufen? Die Scan-Station kann danach keine Dokumente mehr senden.`)) return;
      try { await json(`/api/capture-tokens/${encodeURIComponent(token.id)}`, { method: 'DELETE' }); render(await json('/api/capture-tokens')); message.textContent = 'Zugang widerrufen.'; }
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
    secret.hidden = false; secret.replaceChildren(node('b', 'Zugang „' + created.token.name + '" angelegt. Bitte jetzt kopieren; später ist er nicht mehr sichtbar:'));
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
