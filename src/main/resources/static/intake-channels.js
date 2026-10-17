/* Settings box "Eingangskanäle": channels that fetch documents into the inbox by themselves (watched folder for scanners; mailbox follows). */
(() => {
 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 const stamp = value => value ? new Date(value).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' }) : '–';
 const TYPES = { FOLDER: 'Ordner (Scan to Folder)' };
 /** One line of the list: type, name, state, interval and the last run with its result. */
 function rowText(channel) {
  const state = channel.enabled ? `an, alle ${channel.intervalMinutes} Min.` : 'aus';
  const last = channel.lastRunAt ? `zuletzt ${stamp(channel.lastRunAt)}: ${channel.lastResult || '–'}` : 'noch nie gelaufen';
  return `${TYPES[channel.type] || channel.type} · ${channel.name} · ${state} · ${last}`;
 }
 /** True when the last run ended with an error (shown in red). */
 const failed = channel => /^Fehler/.test(channel.lastResult || '');
 globalThis.LcIntakeChannels = { rowText, failed, TYPES };
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
   node('p', 'Ein Ordner-Kanal liest Dateien, die ein Scanner (Scan to Folder) im Eingangsordner ablegt, und legt sie im Posteingang ab. Erledigte Dateien wandern nach „verarbeitet“, abgelehnte nach „fehler“. Die Ordner richtet der Administrator des Servers als Freigabe für den Scanner ein; der Pfad steht unter dem Kanal. PDF mit Textebene (durchsuchbares PDF) wird direkt genutzt.'),
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
    const toggle = node('button', channel.enabled ? 'Ausschalten' : 'Einschalten', 'secondary'); toggle.type = 'button';
    toggle.onclick = () => act(() => json(`/api/intake/channels/${encodeURIComponent(channel.id)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled: !channel.enabled }) }));
    const run = node('button', 'Jetzt prüfen', 'secondary'); run.type = 'button';
    run.onclick = () => act(() => json(`/api/intake/channels/${encodeURIComponent(channel.id)}/run`, { method: 'POST' }), 'Der Kanal wurde geprüft; das Ergebnis steht in der Zeile.');
    const remove = node('button', 'Löschen', 'secondary'); remove.type = 'button';
    remove.onclick = async () => {
     if (typeof confirmAction === 'function' && !await confirmAction(`Kanal „${channel.name}" löschen? Dateien im Ordner bleiben liegen, werden aber nicht mehr abgeholt.`)) return;
     act(() => json(`/api/intake/channels/${encodeURIComponent(channel.id)}`, { method: 'DELETE' }), 'Kanal gelöscht.');
    };
    item.append(toggle, run, remove); list.append(item);
   });
  }
  create.onclick = async () => {
   create.disabled = true;
   await act(async () => { await json('/api/intake/channels', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type: 'FOLDER', name: name.value.trim(), enabled: true, intervalMinutes: Number(interval.value) }) }); name.value = ''; }, 'Kanal angelegt und eingeschaltet.');
   create.disabled = false;
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
