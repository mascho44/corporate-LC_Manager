/* Deadline calendar: month grid of LC deadlines and open tasks. Click an entry to open the file. */
(() => {
 const KINDS = { expiry: 'Ablauf', shipment: 'Versand', 'follow-up': 'Wiedervorlage', presentation: 'Vorlage', maturity: 'Fälligkeit', task: 'Aufgabe' };
 const pad = n => String(n).padStart(2, '0');
 const iso = (y, m, d) => `${y}-${pad(m + 1)}-${pad(d)}`;

 /** Weeks (Monday first) of a month; each cell has its ISO date, whether it belongs to the month, and its events. */
 function buildMonth(year, month, events) {
  const byDay = {};
  (events || []).forEach(e => { (byDay[e.date] = byDay[e.date] || []).push(e); });
  const first = new Date(Date.UTC(year, month, 1)), offset = (first.getUTCDay() + 6) % 7, start = Date.UTC(year, month, 1 - offset);
  const total = Math.ceil((offset + new Date(Date.UTC(year, month + 1, 0)).getUTCDate()) / 7) * 7, weeks = [];
  for (let i = 0; i < total; i++) {
   const d = new Date(start + i * 86400000), date = iso(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
   if (i % 7 === 0) weeks.push([]);
   weeks[weeks.length - 1].push({ date, day: d.getUTCDate(), inMonth: d.getUTCMonth() === month, events: byDay[date] || [] });
  }
  return weeks;
 }
 /** First and last visible day of the month grid, used as the query range. */
 function range(year, month) {
  const weeks = buildMonth(year, month, []);
  return { from: weeks[0][0].date, to: weeks[weeks.length - 1][6].date };
 }
 globalThis.LcDeadlineCalendar = { buildMonth, range, KINDS };
 if (typeof document === 'undefined' || !document.body) return;

 const node = (tag, text, className) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (className) n.className = className; return n; };
 const MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
 let section = null, grid = null, title = null, scope = null, cursor = new Date();

 async function refresh() {
  const y = cursor.getFullYear(), m = cursor.getMonth(), r = range(y, m);
  title.textContent = `${MONTHS[m]} ${y}`;
  let events = [];
  try { events = await json(`/api/calendar/events?scope=${encodeURIComponent(scope.value)}&from=${r.from}&to=${r.to}`); } catch (e) { grid.replaceChildren(node('p', 'Kalender konnte nicht geladen werden: ' + e.message)); return; }
  const today = new Date().toISOString().slice(0, 10), table = node('div', undefined, 'dl-cal-grid');
  ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'].forEach(d => table.append(node('div', d, 'dl-cal-head')));
  buildMonth(y, m, events).flat().forEach(cell => {
   const box = node('div', undefined, 'dl-cal-cell' + (cell.inMonth ? '' : ' dl-out') + (cell.date === today ? ' dl-today' : ''));
   box.append(node('span', String(cell.day), 'dl-day'));
   cell.events.forEach(e => {
    const item = node('button', `${e.reference || 'Akte'} · ${e.title}`, 'dl-event dl-' + e.kind + (cell.date < today ? ' dl-past' : ''));
    item.type = 'button'; item.title = `${KINDS[e.kind] || e.kind}: ${e.title}${e.reference ? ' – ' + e.reference : ''}${e.assignedTo ? ' (' + e.assignedTo + ')' : ''}`;
    item.onclick = () => e.lcId && show(e.lcId);
    box.append(item);
   });
   table.append(box);
  });
  grid.replaceChildren(table);
 }

 function setup() {
  if (typeof json !== 'function' || typeof appNavigate !== 'function' || section) return;
  const nav = node('button', 'Fristenkalender'); nav.type = 'button'; nav.id = 'appNavCalendar';
  document.querySelector('#appNav nav')?.append(nav);
  section = document.createElement('section'); section.id = 'deadlineCalendarSection'; section.className = 'panel hidden';
  const bar = node('div', undefined, 'dl-cal-bar'), step = (label, delta) => { const b = node('button', label, 'secondary'); b.type = 'button'; b.onclick = () => { cursor = new Date(cursor.getFullYear(), cursor.getMonth() + delta, 1); refresh(); }; return b; };
  title = node('h2', '');
  scope = document.createElement('select');
  [['all', 'Alle'], ['mine', 'Meine Arbeit'], ['unassigned', 'Nicht zugewiesen']].forEach(([v, t]) => { const o = node('option', t); o.value = v; scope.append(o); });
  scope.onchange = refresh;
  const today = node('button', 'Heute', 'secondary'); today.type = 'button'; today.onclick = () => { cursor = new Date(); refresh(); };
  bar.append(step('‹', -1), title, step('›', 1), today, scope);
  grid = node('div');
  section.append(bar, grid);
  document.querySelector('main')?.append(section);
  nav.onclick = () => { appNavigate('deadlineCalendar', nav); return refresh(); };
 }
 const timer = setInterval(() => { if (typeof appNavigate === 'function' && typeof json === 'function') { clearInterval(timer); setup(); } }, 300);
})();
