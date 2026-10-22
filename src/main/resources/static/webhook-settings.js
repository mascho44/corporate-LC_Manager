/* Settings page "Webhooks": target URLs that receive domain events, signed with a secret that is shown once. */
async function setupWebhooks() {
 if (typeof can !== 'function' || !can('SETTINGS_MANAGE')) return;
 let hooks, events;
 try { [hooks, events] = await Promise.all([json('/api/webhooks'), json('/api/webhooks/events')]); } catch { return; }
 const node = (tag, value, cls) => { const n = document.createElement(tag); if (value !== undefined) n.textContent = value; if (cls) n.className = cls; return n; };
 const english = () => globalThis.LcI18n?.language?.() === 'en';
 const eventName = e => english() ? e.english : e.german;
 const nav = node('button', tx('wh.nav', 'Webhooks')); nav.type = 'button'; nav.id = 'appNavWebhooks'; nav.dataset.i18n = 'wh.nav';
 document.querySelector('#appNav nav').append(nav);
 const section = document.createElement('section'); section.id = 'webhookSection'; section.className = 'panel hidden';
 const message = node('p', ''); message.setAttribute('role', 'status');
 const secretBox = document.createElement('div'); const list = document.createElement('div'); const detail = document.createElement('div'); const formBox = document.createElement('div');
 section.append(node('h2', tx('wh.title', 'Webhooks')), node('p', tx('wh.intro', 'Externe Systeme erhalten Ereignisse (z. B. Dokument hochgeladen, Prüfung abgeschlossen) per HTTPS-POST. Jede Nachricht ist mit dem Geheimnis des Webhooks signiert (HMAC-SHA256).')), message, secretBox, list, detail, formBox);
 document.querySelector('main').append(section);
 const when = value => String(value || '').replace('T', ' ').slice(0, 16);
 const statusText = s => ({ DELIVERED: tx('wh.s.delivered', 'zugestellt'), RETRYING: tx('wh.s.retrying', 'wird wiederholt'), DEAD: tx('wh.s.dead', 'fehlgeschlagen'), PENDING: tx('wh.s.pending', 'wartet') }[s] || s || '–');

 function showSecret(created) {
  secretBox.replaceChildren();
  const box = node('div', undefined, 'wh-secret'); box.setAttribute('role', 'alert');
  const code = node('code', created.secret);
  const copy = node('button', tx('wh.copy', 'Kopieren'), 'secondary'); copy.type = 'button'; copy.onclick = () => navigator.clipboard?.writeText(created.secret).then(() => { copy.textContent = tx('wh.copied', 'Kopiert'); });
  const close = node('button', tx('wh.close', 'Schließen'), 'secondary'); close.type = 'button'; close.onclick = () => secretBox.replaceChildren();
  box.append(node('b', tx('wh.secretTitle', 'Geheimnis für „{name}“', { name: created.webhook.name })), node('p', tx('wh.secretHint', 'Es wird nur jetzt angezeigt. Der Empfänger prüft damit die Signatur (Kopfzeile X-LCM-Signature).')), code, copy, close);
  secretBox.append(box);
 }
 function draw() {
  list.replaceChildren();
  if (!hooks.length) list.append(node('p', tx('wh.none', 'Noch keine Webhooks.')));
  hooks.forEach(hook => {
   const row = node('article', undefined, 'membership-row wh-hook' + (hook.active ? '' : ' inactive'));
   const text = node('div'); text.append(node('b', hook.name + (hook.active ? '' : ' · ' + tx('wh.inactive', 'inaktiv'))));
   const kinds = hook.eventTypes.includes('*') ? tx('wh.allEvents', 'alle Ereignisse') : hook.eventTypes.map(n => { const e = events.find(x => x.name === n); return e ? eventName(e) : n; }).join(', ');
   text.append(node('small', hook.url + ' · ' + kinds), node('small', tx('wh.last', 'Letzte Zustellung: {when} ({status})', { when: hook.lastDeliveryAt ? when(hook.lastDeliveryAt) : '–', status: statusText(hook.lastStatus) })));
   const buttons = node('div', undefined, 'wh-buttons');
   const btn = (label, fn) => { const b = node('button', label, 'secondary'); b.type = 'button'; b.onclick = fn; buttons.append(b); return b; };
   btn(tx('wh.edit', 'Bearbeiten'), () => form(hook));
   btn(tx('wh.test', 'Testen'), async () => { try { await json('/api/webhooks/' + hook.id + '/test', { method: 'POST' }); message.textContent = tx('wh.testQueued', 'Testereignis wird gesendet.'); setTimeout(() => showDeliveries(hook), 7000); } catch (error) { message.textContent = error.message; } });
   btn(tx('wh.deliveries', 'Zustellungen'), () => showDeliveries(hook));
   btn(tx('wh.rotate', 'Neues Geheimnis'), async () => {
    if (!await confirmAction(tx('wh.confirmRotate', 'Neues Geheimnis für „{name}“ erzeugen? Das alte gilt sofort nicht mehr.', { name: hook.name }))) return;
    try { showSecret(await json('/api/webhooks/' + hook.id + '/secret', { method: 'POST' })); } catch (error) { message.textContent = error.message; }
   });
   btn(tx('wh.delete', 'Löschen'), async () => {
    if (!await confirmAction(tx('wh.confirmDelete', 'Webhook „{name}“ mit allen Zustellungen löschen?', { name: hook.name }))) return;
    try { await json('/api/webhooks/' + hook.id, { method: 'DELETE' }); hooks = hooks.filter(h => h.id !== hook.id); detail.replaceChildren(); message.textContent = tx('wh.deleted', 'Webhook gelöscht.'); draw(); } catch (error) { message.textContent = error.message; }
   });
   row.append(text, buttons); list.append(row);
  });
  const add = node('button', tx('wh.add', 'Webhook hinzufügen')); add.type = 'button'; add.className = 'wh-add'; add.onclick = () => form(null); list.append(add);
 }
 async function showDeliveries(hook) {
  detail.replaceChildren();
  let rows; try { rows = await json('/api/webhooks/' + hook.id + '/deliveries'); } catch (error) { message.textContent = error.message; return; }
  hooks = await json('/api/webhooks').catch(() => hooks); draw();
  detail.append(node('h3', tx('wh.deliveriesOf', 'Zustellungen von „{name}“', { name: hook.name })));
  if (!rows.length) { detail.append(node('p', tx('wh.noDeliveries', 'Noch keine Zustellungen.'))); return; }
  const ul = node('ul', undefined, 'sla-list');
  rows.forEach(d => {
   const li = node('li', tx('wh.deliveryLine', '{when} · {event} · {status} · Versuch {attempts}{detail}', { when: when(d.createdAt), event: d.eventType, status: statusText(d.status), attempts: d.attempts, detail: d.lastError ? ' · ' + d.lastError : '' }), d.status === 'DELIVERED' ? 'ok' : d.status === 'DEAD' ? 'bad' : 'warn');
   if (d.status === 'DEAD') { const retry = node('button', tx('wh.retry', 'Erneut senden'), 'secondary'); retry.type = 'button'; retry.onclick = async () => { try { await json('/api/webhooks/deliveries/' + d.id + '/retry', { method: 'POST' }); showDeliveries(hook); } catch (error) { message.textContent = error.message; } }; li.append(' ', retry); }
   ul.append(li);
  });
  detail.append(ul);
 }
 function form(hook) {
  formBox.replaceChildren(); message.textContent = '';
  const f = document.createElement('form'); f.className = 'form-grid wh-form';
  const field = (label, control) => { const l = node('label', label); l.append(control); f.append(l); return control; };
  const name = field(tx('wh.name', 'Name'), document.createElement('input')); name.required = true; name.maxLength = 120; name.value = hook?.name || '';
  const url = field(tx('wh.url', 'Adresse (https://…)'), document.createElement('input')); url.required = true; url.maxLength = 500; url.type = 'url'; url.value = hook?.url || '';
  const box = node('fieldset', undefined, 'wh-events'); box.append(node('legend', tx('wh.events', 'Ereignisse')));
  const all = document.createElement('input'); all.type = 'checkbox'; all.value = '*'; all.checked = !hook || hook.eventTypes.includes('*'); const allLabel = node('label', tx('wh.allEvents', 'alle Ereignisse')); allLabel.prepend(all); box.append(allLabel);
  const boxes = events.map(e => { const c = document.createElement('input'); c.type = 'checkbox'; c.value = e.name; c.checked = Boolean(hook?.eventTypes.includes(e.name)); const l = node('label', eventName(e)); l.prepend(c); box.append(l); return c; });
  const sync = () => boxes.forEach(c => { c.disabled = all.checked; }); all.onchange = sync; sync(); f.append(box);
  const active = document.createElement('input'); active.type = 'checkbox'; active.checked = hook ? hook.active : true; const activeLabel = node('label', tx('wh.active', 'Aktiv')); activeLabel.prepend(active); f.append(activeLabel);
  const save = node('button', tx('wh.save', 'Speichern')); save.type = 'submit'; const cancel = node('button', tx('wh.cancel', 'Abbrechen'), 'secondary'); cancel.type = 'button'; cancel.onclick = () => formBox.replaceChildren();
  const bar = document.createElement('div'); bar.className = 'wh-actions'; bar.append(save, cancel); f.append(bar);
  f.onsubmit = async event => {
   event.preventDefault();
   const body = { name: name.value, url: url.value, eventTypes: all.checked ? ['*'] : boxes.filter(c => c.checked).map(c => c.value), active: active.checked };
   try {
    const saved = await json(hook ? '/api/webhooks/' + hook.id : '/api/webhooks', { method: hook ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    const view = hook ? saved : saved.webhook;
    hooks = (hook ? hooks.map(h => h.id === view.id ? view : h) : [...hooks, view]).sort((a, b) => a.name.localeCompare(b.name));
    formBox.replaceChildren(); message.textContent = tx('wh.saved', 'Webhook gespeichert.'); draw(); if (!hook) showSecret(saved);
   } catch (error) { message.textContent = error.message; }
  };
  formBox.append(f);
 }
 nav.onclick = () => { appNavigate('webhooks', nav); draw(); };
 draw();
}
