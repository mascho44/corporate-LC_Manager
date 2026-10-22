/* Settings page "API-Schlüssel": keys for the external REST API (/api/v1), each with scopes; the secret is shown once. */
async function setupApiKeys() {
 if (typeof can !== 'function' || !can('SETTINGS_MANAGE')) return;
 let keys, scopes;
 try { [keys, scopes] = await Promise.all([json('/api/api-keys'), json('/api/api-keys/scopes')]); } catch { return; }
 const node = (tag, value, cls) => { const n = document.createElement(tag); if (value !== undefined) n.textContent = value; if (cls) n.className = cls; return n; };
 const scopeName = s => tx('apik.scope.' + s, s);
 const nav = node('button', tx('apik.nav', 'API-Schlüssel')); nav.type = 'button'; nav.id = 'appNavApiKeys'; nav.dataset.i18n = 'apik.nav';
 document.querySelector('#appNav nav').append(nav);
 const section = document.createElement('section'); section.id = 'apiKeySection'; section.className = 'panel hidden';
 const message = node('p', ''); message.setAttribute('role', 'status');
 const secretBox = document.createElement('div'); const list = document.createElement('div'); const formBox = document.createElement('div');
 section.append(node('h2', tx('apik.title', 'API-Schlüssel')), node('p', tx('apik.intro', 'Externe Systeme greifen mit einem API-Schlüssel auf die REST-API unter /api/v1 zu: Akten und Befunde lesen, Akten aus einer MT700 anlegen, Dokumente hochladen. Jeder Schlüssel hat nur die Berechtigungen, die Sie ihm geben.')), message, secretBox, list, formBox);
 document.querySelector('main').append(section);
 const when = value => String(value || '').replace('T', ' ').slice(0, 16);

 function showSecret(created) {
  secretBox.replaceChildren();
  const box = node('div', undefined, 'wh-secret'); box.setAttribute('role', 'alert');
  const code = node('code', created.secret);
  const example = node('code', 'curl -H "Authorization: Bearer ' + created.secret + '" ' + location.origin + '/api/v1/lcs');
  const copy = node('button', tx('apik.copy', 'Kopieren'), 'secondary'); copy.type = 'button'; copy.onclick = () => navigator.clipboard?.writeText(created.secret).then(() => { copy.textContent = tx('apik.copied', 'Kopiert'); });
  const close = node('button', tx('apik.close', 'Schließen'), 'secondary'); close.type = 'button'; close.onclick = () => secretBox.replaceChildren();
  box.append(node('b', tx('apik.secretTitle', 'Schlüssel für „{name}“', { name: created.key.name })), node('p', tx('apik.secretHint', 'Er wird nur jetzt angezeigt. Bewahren Sie ihn sicher auf; bei Verlust widerrufen Sie ihn und legen einen neuen an.')), code, example, copy, close);
  secretBox.append(box);
 }
 function draw() {
  list.replaceChildren();
  if (!keys.length) list.append(node('p', tx('apik.none', 'Noch keine API-Schlüssel.')));
  keys.forEach(key => {
   const row = node('article', undefined, 'membership-row api-key' + (key.revoked ? ' inactive' : ''));
   const text = node('div'); text.append(node('b', key.name + ' · ' + key.prefix + '…' + (key.revoked ? ' · ' + tx('apik.revoked', 'widerrufen') : '')));
   text.append(node('small', key.scopes.map(scopeName).join(', ') + ' · ' + tx('apik.lastUsed', 'zuletzt benutzt: {when}', { when: key.lastUsedAt ? when(key.lastUsedAt) : '–' })));
   row.append(text);
   if (!key.revoked) {
    const revoke = node('button', tx('apik.revoke', 'Widerrufen'), 'secondary'); revoke.type = 'button';
    revoke.onclick = async () => {
     if (!await confirmAction(tx('apik.confirmRevoke', 'API-Schlüssel „{name}“ widerrufen? Systeme, die ihn nutzen, verlieren sofort den Zugriff.', { name: key.name }))) return;
     try { const view = await json('/api/api-keys/' + key.id, { method: 'DELETE' }); keys = keys.map(k => k.id === view.id ? view : k); message.textContent = tx('apik.revokedDone', 'API-Schlüssel widerrufen.'); draw(); } catch (error) { message.textContent = error.message; }
    };
    row.append(revoke);
   }
   list.append(row);
  });
  const add = node('button', tx('apik.add', 'API-Schlüssel anlegen')); add.type = 'button'; add.className = 'wh-add'; add.onclick = form; list.append(add);
 }
 function form() {
  formBox.replaceChildren(); message.textContent = '';
  const f = document.createElement('form'); f.className = 'form-grid wh-form';
  const nameLabel = node('label', tx('apik.name', 'Name (z. B. ERP-Anbindung)')); const name = document.createElement('input'); name.required = true; name.maxLength = 100; nameLabel.append(name); f.append(nameLabel);
  const box = node('fieldset', undefined, 'wh-events'); box.append(node('legend', tx('apik.scopes', 'Berechtigungen')));
  const boxes = scopes.scopes.map(s => { const c = document.createElement('input'); c.type = 'checkbox'; c.value = s; c.checked = s === 'READ'; const l = node('label', scopeName(s) + ' – ' + tx('apik.scopeHint.' + s, s)); l.prepend(c); box.append(l); return c; });
  f.append(box);
  const save = node('button', tx('apik.create', 'Anlegen')); save.type = 'submit'; const cancel = node('button', tx('apik.cancel', 'Abbrechen'), 'secondary'); cancel.type = 'button'; cancel.onclick = () => formBox.replaceChildren();
  const bar = document.createElement('div'); bar.className = 'wh-actions'; bar.append(save, cancel); f.append(bar);
  f.onsubmit = async event => {
   event.preventDefault();
   try {
    const created = await json('/api/api-keys', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: name.value, scopes: boxes.filter(c => c.checked).map(c => c.value) }) });
    keys = [created.key, ...keys]; formBox.replaceChildren(); message.textContent = tx('apik.created', 'API-Schlüssel angelegt.'); draw(); showSecret(created);
   } catch (error) { message.textContent = error.message; }
  };
  formBox.append(f);
 }
 nav.onclick = () => { appNavigate('apiKeys', nav); draw(); };
 draw();
}
