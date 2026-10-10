/* "Heute zu tun": one row of tiles on the start page that tells what is waiting, with a way to jump there. */
(() => {
 /** Pure model: which tiles to show for the given counts. null means "not available to this user" and hides the tile. */
 function tiles(m) {
  const list = [];
  const add = (key, label, count, hint, tone) => { if (count === null || count === undefined) return; list.push({ key, label, count, hint, tone: count > 0 ? tone : 'ok' }); };
  add('overdue', 'Überfällige Fristen', m.overdue, 'Fristen prüfen', 'bad');
  add('ebics', 'Neue EBICS-Nachrichten', m.ebics, 'Ansehen und importieren', 'info');
  add('inbox', 'Posteingang offen', m.inbox, 'Dokumente zuordnen', 'info');
  add('waiting', 'Warten auf Kunde', m.waiting, 'Nachfassen', 'warn');
  add('upcoming', 'Fristen in 7 Tagen', m.upcoming, 'Vorbereiten', 'warn');
  add('unassigned', 'Ohne Bearbeiter', m.unassigned, 'Zuweisen', 'warn');
  return list;
 }
 const symbol = tone => tone === 'ok' ? '✓' : tone === 'bad' ? '!' : tone === 'warn' ? '!' : 'i';
 globalThis.LcWorklist = { tiles, symbol };
 if (typeof document === 'undefined' || !document.body) return;

 const num = selector => { const n = Number(document.querySelector(selector)?.textContent); return Number.isFinite(n) ? n : null; };
 const safe = async (task) => { try { return await task(); } catch (error) { return null; } };
 const permitted = permission => typeof can === 'function' && can(permission);
 let section = null, timer = null, loading = false;

 async function collect() {
  const lcs = typeof data !== 'undefined' && Array.isArray(data) ? data : [];
  return {
   overdue: num('#queueOverdue'),
   upcoming: num('#queueUpcoming'),
   unassigned: num('#queueUnassigned'),
   waiting: lcs.filter(lc => lc.status === 'WAITING_FOR_CUSTOMER').length,
   inbox: permitted('DOCUMENT_UPLOAD') ? await safe(async () => (await json('/api/inbox')).length) : null,
   ebics: permitted('SETTINGS_MANAGE') ? await safe(async () => { const c = await json('/api/ebics/connection'); return c && c.status === 'ACTIVE' ? Number(c.newMessages) || 0 : null; }) : null
  };
 }
 function open(key) {
  const click = id => document.querySelector(id)?.click();
  if (key === 'inbox') click('#appNavInbox');
  else if (key === 'ebics') click('#appNavEbics');
  else {
   const filter = document.querySelector('#workQueueFilter');
   if (filter) { filter.value = key === 'unassigned' ? 'unassigned' : 'all'; filter.dispatchEvent(new Event('change')); }
   document.querySelector('#workQueueSection')?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  }
 }
 async function refresh(force) {
  if (loading || !section || (document.hidden && force !== true)) return;
  loading = true;
  try {
   const model = tiles(await collect());
   section.replaceChildren();
   const head = document.createElement('div'); head.className = 'today-head';
   const title = document.createElement('h2'); title.textContent = 'Heute zu tun';
   head.append(title); section.append(head);
   const row = document.createElement('div'); row.className = 'today-tiles';
   model.forEach(tile => {
    const button = document.createElement('button'); button.type = 'button'; button.className = 'today-tile ' + tile.tone;
    button.setAttribute('aria-label', `${tile.label}: ${tile.count}. ${tile.count > 0 ? tile.hint : 'Nichts offen'}`);
    const icon = document.createElement('span'); icon.className = 'today-icon'; icon.setAttribute('aria-hidden', 'true'); icon.textContent = symbol(tile.tone);
    const count = document.createElement('b'); count.textContent = String(tile.count);
    const label = document.createElement('span'); label.textContent = tile.label;
    const hint = document.createElement('small'); hint.textContent = tile.count > 0 ? tile.hint : 'Nichts offen';
    button.append(icon, count, label, hint); button.onclick = () => open(tile.key);
    row.append(button);
   });
   section.append(row);
   section.hidden = model.length === 0;
  } finally { loading = false; }
 }
 function start() {
  const queue = document.querySelector('#workQueueSection');
  if (!queue || section) return;
  section = document.createElement('section'); section.id = 'todaySection'; section.className = 'panel today'; section.setAttribute('aria-live', 'polite');
  queue.before(section);
  refresh(true); timer = setInterval(() => refresh(), 60000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  document.addEventListener('click', event => { if (event.target.closest?.('#workQueueList,[data-app-section="cockpit"]')) setTimeout(refresh, 300); });
 }
 // The cockpit data arrives after login; wait until the work queue exists and the first load is complete.
 const wait = setInterval(() => { if (typeof data !== 'undefined' && typeof currentUser !== 'undefined' && currentUser && document.querySelector('#workQueueSection')) { clearInterval(wait); setTimeout(start, 800); } }, 400);
})();
