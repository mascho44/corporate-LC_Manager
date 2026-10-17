/* Settings box "Eingangskanäle": channels that fetch documents into the inbox by themselves (watched folder for scanners; mailbox follows). */
(() => {
 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 const stamp = value => value ? new Date(value).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' }) : '–';
 const TYPES = { FOLDER: 'Ordner (Scan to Folder)', EMAIL: 'E-Mail-Postfach (IMAP)' };
 /** One line of the list: type, name, state, interval and the last run with its result. */
 function rowText(channel) {
  const state = channel.enabled ? `an, alle ${channel.intervalMinutes} Min.` : 'aus';
  const last = channel.lastRunAt ? `zuletzt ${stamp(channel.lastRunAt)}: ${channel.lastResult || '–'}` : 'noch nie gelaufen';
  return `${TYPES[channel.type] || channel.type} · ${channel.name} · ${state} · ${last}`;
 }
 /** Second line of a mailbox channel: server, folder, senders and whether a password is stored (never the password itself). */
 function mailText(channel) {
  const m = channel.mail; if (!m) return null;
  return `${m.username}@${m.host}:${m.port} (${m.security}) · Ordner ${m.folder} · Absender: ${(m.allowedSenders || []).join(', ') || 'keine freigegeben'} · Kennwort ${m.passwordSet ? 'hinterlegt' : 'fehlt'}`;
 }
 /** Mailbox part of a request from the form values; an empty password is left out so the stored one stays. */
 function mailRequest(v) {
  const senders = String(v.senders || '').split(/[\n,;]+/).map(x => x.trim()).filter(Boolean);
  const r = { host: v.host.trim(), security: v.security, username: v.username.trim(), allowedSenders: senders, folder: v.folder.trim() || null, processedFolder: v.processedFolder.trim() || null, rejectedFolder: v.rejectedFolder.trim() || null };
  if (v.port !== '' && v.port != null) r.port = Number(v.port);
  if (v.password) r.password = v.password;
  return r;
 }
 /** True when the last run ended with an error (shown in red). */
 const failed = channel => /^Fehler/.test(channel.lastResult || '');
 globalThis.LcIntakeChannels = { rowText, failed, mailText, mailRequest, TYPES };
 if (typeof document === 'undefined' || !document.body) return;

 async function build(host) {
  const box = node('section', undefined, 'settings-box'); box.id = 'intakeChannelBox';
  const message = node('p', '', 'settings-status'); message.setAttribute('role', 'status');
  const list = node('div', undefined, 'settings-list');
  const name = document.createElement('input'); name.maxLength = 40; name.placeholder = 'z. B. poststelle'; name.setAttribute('aria-label', 'Name des Kanals (Kleinbuchstaben, Ziffern, Bindestrich)');
  const interval = document.createElement('input'); interval.type = 'number'; interval.min = '1'; interval.max = '1440'; interval.value = '2'; interval.style.width = '5em'; interval.setAttribute('aria-label', 'Abruf alle … Minuten');
  const create = node('button', 'Ordner-Kanal anlegen'); create.type = 'button';
  const row = node('div', undefined, 'settings-inline'); row.append(name, node('span', 'alle'), interval, node('span', 'Min.'), create);
  box.append(node('h3', 'Eingangskanäle'),
   node('p', 'Ein Ordner-Kanal liest Dateien, die ein Scanner (Scan to Folder) im Eingangsordner ablegt, ein E-Mail-Kanal die Anhänge eines Postfachs, und legt sie im Posteingang ab. Erledigte Dateien wandern nach „verarbeitet“, abgelehnte nach „fehler“. Die Ordner richtet der Administrator des Servers als Freigabe für den Scanner ein; der Pfad steht unter dem Kanal. PDF mit Textebene (durchsuchbares PDF) wird direkt genutzt.'),
   row, message, list);
  host.append(box);
  const reload = async () => render(await json('/api/intake/channels'));
  const act = async (fn, done) => { message.textContent = ''; try { await fn(); await reload(); if (done) message.textContent = done; } catch (error) { message.textContent = error.message; } };
  function render(channels) {
   list.replaceChildren();
   if (!channels.length) list.append(node('p', 'Noch kein Kanal angelegt.'));
   channels.forEach(channel => {
    const item = node('div', undefined, 'settings-row'); const text = node('span', rowText(channel)); if (failed(channel)) text.style.color = '#b42318'; item.append(text);
    if (channel.folder) { const where = node('code', channel.folder); where.style.display = 'block'; where.style.wordBreak = 'break-all'; item.append(where); }
    const mailLine = mailText(channel); if (mailLine) { const detail = node('small', mailLine); detail.style.display = 'block'; item.append(detail); }
    const toggle = node('button', channel.enabled ? 'Ausschalten' : 'Einschalten', 'secondary'); toggle.type = 'button';
    toggle.onclick = () => act(() => json(`/api/intake/channels/${encodeURIComponent(channel.id)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled: !channel.enabled }) }));
    const run = node('button', 'Jetzt prüfen', 'secondary'); run.type = 'button';
    run.onclick = () => act(() => json(`/api/intake/channels/${encodeURIComponent(channel.id)}/run`, { method: 'POST' }), 'Der Kanal wurde geprüft; das Ergebnis steht in der Zeile.');
    const remove = node('button', 'Löschen', 'secondary'); remove.type = 'button';
    remove.onclick = async () => {
     if (typeof confirmAction === 'function' && !await confirmAction(`Kanal „${channel.name}" löschen? Dateien im Ordner bleiben liegen, werden aber nicht mehr abgeholt.`)) return;
     act(() => json(`/api/intake/channels/${encodeURIComponent(channel.id)}`, { method: 'DELETE' }), 'Kanal gelöscht.');
    };
    item.append(toggle, run);
    if (channel.type === 'EMAIL') {
     const test = node('button', 'Verbindung testen', 'secondary'); test.type = 'button';
     test.onclick = async () => { message.textContent = ''; try { message.textContent = (await json(`/api/intake/channels/${encodeURIComponent(channel.id)}/test`, { method: 'POST' })).result; } catch (error) { message.textContent = error.message; } };
     const edit = node('button', 'Bearbeiten', 'secondary'); edit.type = 'button'; edit.onclick = () => window.__intakeEditMail?.(channel);
     item.append(test, edit);
    }
    item.append(remove); list.append(item);
   });
  }
  create.onclick = async () => {
   create.disabled = true;
   await act(async () => { await json('/api/intake/channels', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type: 'FOLDER', name: name.value.trim(), enabled: true, intervalMinutes: Number(interval.value) }) }); name.value = ''; }, 'Kanal angelegt und eingeschaltet.');
   create.disabled = false;
  };
  // Mailbox form: create and edit (the stored password is never shown; empty = unchanged)
  const mail = node('details', undefined, 'settings-inline'); mail.append(node('summary', 'E-Mail-Postfach einrichten'));
  const field = (label, el) => { const l = node('label', label); l.style.display = 'block'; l.append(el); return l; };
  const input = (placeholder, type = 'text', max = 255) => { const i = document.createElement('input'); i.type = type; i.maxLength = max; if (placeholder) i.placeholder = placeholder; return i; };
  const m = { name: input('z. B. einkauf-post', 'text', 40), host: input('imap.kunde.example'), port: input('993 / 143', 'number', 5), username: input('Benutzername'), password: input('Kennwort (leer = unverändert)', 'password', 500),
   folder: input('INBOX'), processedFolder: input('LC-verarbeitet', 'text', 100), rejectedFolder: input('LC-abgelehnt', 'text', 100) };
  m.password.autocomplete = 'new-password';
  m.security = document.createElement('select'); [['SSL', 'SSL (IMAPS, Port 993)'], ['STARTTLS', 'STARTTLS (Port 143)']].forEach(([v, t]) => { const o = node('option', t); o.value = v; m.security.append(o); });
  m.senders = document.createElement('textarea'); m.senders.rows = 3; m.senders.placeholder = 'einkauf@kunde.example\n@partner.example (ganze Domain)'; m.senders.setAttribute('aria-label', 'Freigegebene Absender');
  m.interval = input('', 'number', 4); m.interval.value = '2'; m.interval.min = '1'; m.interval.max = '1440';
  const save = node('button', 'Postfach speichern'); save.type = 'button'; const cancel = node('button', 'Abbrechen', 'secondary'); cancel.type = 'button'; cancel.hidden = true;
  mail.append(node('p', 'Es werden nur Mails der freigegebenen Absender übernommen (Adresse oder @Domain); ohne Liste wird nichts übernommen. Die Verbindung ist immer verschlüsselt. Die Anhänge (PDF, PNG, JPG, TIF bis 10 MB) kommen in den Posteingang, die Mail wandert danach in den Ordner „verarbeitet“, Mails anderer Absender in „abgelehnt“. Das Kennwort wird verschlüsselt gespeichert und nie angezeigt.'),
   field('Name des Kanals', m.name), field('Server', m.host), field('Verschlüsselung', m.security), field('Port (leer = Standard)', m.port), field('Benutzername', m.username), field('Kennwort', m.password),
   field('Ordner', m.folder), field('Ordner für Verarbeitetes', m.processedFolder), field('Ordner für Abgelehntes', m.rejectedFolder), field('Freigegebene Absender (einer je Zeile)', m.senders), field('Abruf alle … Minuten', m.interval), save, cancel);
  box.append(mail);
  let editing = null;
  const resetMail = () => { editing = null; Object.values(m).forEach(e => { if (e.tagName === 'SELECT') e.value = 'SSL'; else e.value = ''; }); m.interval.value = '2'; m.name.disabled = false; save.textContent = 'Postfach speichern'; cancel.hidden = true; };
  cancel.onclick = resetMail;
  window.__intakeEditMail = channel => { editing = channel; const c = channel.mail || {}; m.name.value = channel.name; m.name.disabled = true; m.host.value = c.host || ''; m.security.value = c.security || 'SSL'; m.port.value = c.port || ''; m.username.value = c.username || ''; m.password.value = ''; m.folder.value = c.folder || ''; m.processedFolder.value = c.processedFolder || ''; m.rejectedFolder.value = c.rejectedFolder || ''; m.senders.value = (c.allowedSenders || []).join('\n'); m.interval.value = channel.intervalMinutes; save.textContent = 'Änderungen speichern'; cancel.hidden = false; mail.open = true; mail.scrollIntoView?.({ block: 'nearest' }); };
  save.onclick = async () => {
   save.disabled = true;
   const values = { host: m.host.value, port: m.port.value, security: m.security.value, username: m.username.value, password: m.password.value, folder: m.folder.value, processedFolder: m.processedFolder.value, rejectedFolder: m.rejectedFolder.value, senders: m.senders.value };
   await act(async () => {
    if (editing) await json(`/api/intake/channels/${encodeURIComponent(editing.id)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ intervalMinutes: Number(m.interval.value), mail: mailRequest(values) }) });
    else await json('/api/intake/channels', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type: 'EMAIL', name: m.name.value.trim(), enabled: false, intervalMinutes: Number(m.interval.value), mail: mailRequest(values) }) });
    resetMail();
   }, editing ? 'Postfach gespeichert.' : 'Postfach angelegt (noch ausgeschaltet). Bitte erst „Verbindung testen“, dann einschalten.');
   save.disabled = false;
  };
  try { await reload(); } catch (error) { message.textContent = error.message; }
 }
 const wait = setInterval(() => {
  const host = document.querySelector('#tenantSection');
  if (host && typeof can === 'function' && typeof currentUser !== 'undefined' && currentUser) {
   clearInterval(wait);
   if (can('SETTINGS_MANAGE') && !host.querySelector('#intakeChannelBox')) build(host);
  }
 }, 500);
})();
