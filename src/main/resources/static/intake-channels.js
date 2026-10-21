/* Settings box "Eingangskanäle": channels that fetch documents into the inbox by themselves (watched folder for scanners; mailbox follows). */
(() => {
 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 const stamp = value => value ? new Date(value).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' }) : '–';
 const TYPES = (globalThis.lcLazy || (f => f()))(() => ({ FOLDER: tx('icl.391ed3','Ordner (Scan to Folder)'), EMAIL: tx('icl.5ca981','E-Mail-Postfach (IMAP)') }));
 /** One line of the list: type, name, state, interval and the last run with its result. */
 function rowText(channel) {
  const state = channel.enabled ? tx('ict.409a82','an, alle {p0} Min.',{p0:channel.intervalMinutes}) : 'aus';
  const last = channel.lastRunAt ? tx('icx.last','zuletzt {when}: {result}',{when:stamp(channel.lastRunAt),result:channel.lastResult || '–'}) : tx('icx.never','noch nie gelaufen');
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
  const name = document.createElement('input'); name.maxLength = 40; name.placeholder = 'z. B. poststelle'; name.setAttribute('aria-label', tx('icl.ee3a89','Name des Kanals (Kleinbuchstaben, Ziffern, Bindestrich)'));
  const interval = document.createElement('input'); interval.type = 'number'; interval.min = '1'; interval.max = '1440'; interval.value = '2'; interval.style.width = '5em'; interval.setAttribute('aria-label', tx('icl.76a8bb','Abruf alle … Minuten'));
  const create = node('button', tx('icl.d9db04','Ordner-Kanal anlegen')); create.type = 'button';
  const row = node('div', undefined, 'settings-inline'); row.append(name, node('span', 'alle'), interval, node('span', tx('icl.312186','Min.')), create);
  box.append(node('h3', tx('icl.d808b3','Eingangskanäle')),
   node('p', 'Ein Ordner-Kanal liest Dateien, die ein Scanner (Scan to Folder) im Eingangsordner ablegt, ein E-Mail-Kanal die Anhänge eines Postfachs, und legt sie im Posteingang ab. Erledigte Dateien wandern nach „verarbeitet“, abgelehnte nach „fehler“. Die Ordner richtet der Administrator des Servers als Freigabe für den Scanner ein; der Pfad steht unter dem Kanal. PDF mit Textebene (durchsuchbares PDF) wird direkt genutzt.'),
   row, message, list);
  host.append(box);
  const reload = async () => render(await json('/api/intake/channels'));
  const act = async (fn, done) => { message.textContent = ''; try { await fn(); await reload(); if (done) message.textContent = done; } catch (error) { message.textContent = error.message; } };
  function render(channels) {
   list.replaceChildren();
   if (!channels.length) list.append(node('p', tx('icl.ea0892','Noch kein Kanal angelegt.')));
   channels.forEach(channel => {
    const item = node('div', undefined, 'settings-row'); const text = node('span', rowText(channel)); if (failed(channel)) text.style.color = '#b42318'; item.append(text);
    if (channel.folder) { const where = node('code', channel.folder); where.style.display = 'block'; where.style.wordBreak = 'break-all'; item.append(where); }
    const mailLine = mailText(channel); if (mailLine) { const detail = node('small', mailLine); detail.style.display = 'block'; item.append(detail); }
    const toggle = node('button', channel.enabled ? tx('icl.bda987','Ausschalten') : tx('icl.a56517','Einschalten'), 'secondary'); toggle.type = 'button';
    toggle.onclick = () => act(() => json(`/api/intake/channels/${encodeURIComponent(channel.id)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled: !channel.enabled }) }));
    const run = node('button', tx('icl.5c3738','Jetzt prüfen'), 'secondary'); run.type = 'button';
    run.onclick = () => act(() => json(`/api/intake/channels/${encodeURIComponent(channel.id)}/run`, { method: 'POST' }), tx('icl.a6829b','Der Kanal wurde geprüft; das Ergebnis steht in der Zeile.'));
    const remove = node('button', tx('icl.1010b0','Löschen'), 'secondary'); remove.type = 'button';
    remove.onclick = async () => {
     if (typeof confirmAction === 'function' && !await confirmAction(tx('ict.914749','Kanal „{p0}" löschen? Dateien im Ordner bleiben liegen, werden aber nicht mehr abgeholt.',{p0:channel.name}))) return;
     act(() => json(`/api/intake/channels/${encodeURIComponent(channel.id)}`, { method: 'DELETE' }), tx('icl.4075a6','Kanal gelöscht.'));
    };
    item.append(toggle, run);
    if (channel.type === 'EMAIL') {
     const test = node('button', tx('icl.6a0795','Verbindung testen'), 'secondary'); test.type = 'button';
     test.onclick = async () => { message.textContent = ''; try { message.textContent = (await json(`/api/intake/channels/${encodeURIComponent(channel.id)}/test`, { method: 'POST' })).result; } catch (error) { message.textContent = error.message; } };
     const edit = node('button', tx('icl.5ad05d','Bearbeiten'), 'secondary'); edit.type = 'button'; edit.onclick = () => window.__intakeEditMail?.(channel);
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
  const mail = node('details', undefined, 'settings-inline'); mail.append(node('summary', tx('icl.73e86f','E-Mail-Postfach einrichten')));
  const field = (label, el) => { const l = node('label', label); l.style.display = 'block'; l.append(el); return l; };
  const input = (placeholder, type = 'text', max = 255) => { const i = document.createElement('input'); i.type = type; i.maxLength = max; if (placeholder) i.placeholder = placeholder; return i; };
  const m = { name: input('z. B. einkauf-post', 'text', 40), host: input('imap.kunde.example'), port: input('993 / 143', 'number', 5), username: input(tx('icl.cca849','Benutzername')), password: input(tx('icl.63eae0','Kennwort (leer = unverändert)'), 'password', 500),
   folder: input('INBOX'), processedFolder: input(tx('icl.90f94d','LC-verarbeitet'), 'text', 100), rejectedFolder: input(tx('icl.89b0e8','LC-abgelehnt'), 'text', 100) };
  m.password.autocomplete = 'new-password';
  m.security = document.createElement('select'); [['SSL', tx('icl.078392','SSL (IMAPS, Port 993)')], ['STARTTLS', tx('icl.350268','STARTTLS (Port 143)')]].forEach(([v, t]) => { const o = node('option', t); o.value = v; m.security.append(o); });
  m.senders = document.createElement('textarea'); m.senders.rows = 3; m.senders.placeholder = 'einkauf@kunde.example\n@partner.example (ganze Domain)'; m.senders.setAttribute('aria-label', tx('icl.6e3635','Freigegebene Absender'));
  m.interval = input('', 'number', 4); m.interval.value = '2'; m.interval.min = '1'; m.interval.max = '1440';
  const save = node('button', tx('icl.13af26','Postfach speichern')); save.type = 'button'; const cancel = node('button', tx('icl.4b9727','Abbrechen'), 'secondary'); cancel.type = 'button'; cancel.hidden = true;
  mail.append(node('p', 'Es werden nur Mails der freigegebenen Absender übernommen (Adresse oder @Domain); ohne Liste wird nichts übernommen. Die Verbindung ist immer verschlüsselt. Die Anhänge (PDF, PNG, JPG, TIF bis 10 MB) kommen in den Posteingang, die Mail wandert danach in den Ordner „verarbeitet“, Mails anderer Absender in „abgelehnt“. Das Kennwort wird verschlüsselt gespeichert und nie angezeigt.'),
   field(tx('icl.70bbd0','Name des Kanals'), m.name), field(tx('icl.9aa1b0','Server'), m.host), field(tx('icl.3016f3','Verschlüsselung'), m.security), field(tx('icl.44c473','Port (leer = Standard)'), m.port), field(tx('icl.cca849','Benutzername'), m.username), field(tx('icl.98176b','Kennwort'), m.password),
   field(tx('icl.45a882','Ordner'), m.folder), field(tx('icl.878c85','Ordner für Verarbeitetes'), m.processedFolder), field(tx('icl.78fc17','Ordner für Abgelehntes'), m.rejectedFolder), field(tx('icl.89df30','Freigegebene Absender (einer je Zeile)'), m.senders), field(tx('icl.76a8bb','Abruf alle … Minuten'), m.interval), save, cancel);
  box.append(mail);
  let editing = null;
  const resetMail = () => { editing = null; Object.values(m).forEach(e => { if (e.tagName === 'SELECT') e.value = 'SSL'; else e.value = ''; }); m.interval.value = '2'; m.name.disabled = false; save.textContent = tx('icl.13af26','Postfach speichern'); cancel.hidden = true; };
  cancel.onclick = resetMail;
  window.__intakeEditMail = channel => { editing = channel; const c = channel.mail || {}; m.name.value = channel.name; m.name.disabled = true; m.host.value = c.host || ''; m.security.value = c.security || 'SSL'; m.port.value = c.port || ''; m.username.value = c.username || ''; m.password.value = ''; m.folder.value = c.folder || ''; m.processedFolder.value = c.processedFolder || ''; m.rejectedFolder.value = c.rejectedFolder || ''; m.senders.value = (c.allowedSenders || []).join('\n'); m.interval.value = channel.intervalMinutes; save.textContent = 'Änderungen speichern'; cancel.hidden = false; mail.open = true; mail.scrollIntoView?.({ block: 'nearest' }); };
  save.onclick = async () => {
   save.disabled = true;
   const values = { host: m.host.value, port: m.port.value, security: m.security.value, username: m.username.value, password: m.password.value, folder: m.folder.value, processedFolder: m.processedFolder.value, rejectedFolder: m.rejectedFolder.value, senders: m.senders.value };
   await act(async () => {
    if (editing) await json(`/api/intake/channels/${encodeURIComponent(editing.id)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ intervalMinutes: Number(m.interval.value), mail: mailRequest(values) }) });
    else await json('/api/intake/channels', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type: 'EMAIL', name: m.name.value.trim(), enabled: false, intervalMinutes: Number(m.interval.value), mail: mailRequest(values) }) });
    resetMail();
   }, editing ? tx('icl.32af26','Postfach gespeichert.') : tx('icl.db93fc','Postfach angelegt (noch ausgeschaltet). Bitte erst „Verbindung testen“, dann einschalten.'));
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
