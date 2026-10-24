/* Guarantee cockpit (page "Übersicht" in the menu group "Garantien"): figures, next dates and the use of the credit lines, from the guarantee list and the lines. Presentation only. */
(() => {
 const tr = (key, de, values) => typeof tx === 'function' ? tx(key, de, values) : de;
 const el = (tag, text, cls) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (cls) n.className = cls; return n; };
 const money = (value, currency) => Number(value).toLocaleString(tr('gv.locale', 'de-DE'), { minimumFractionDigits: 0, maximumFractionDigits: 0 }) + ' ' + currency;
 const day = value => value ? (typeof fmtDate === 'function' ? fmtDate(value) : value) : '–';
 const DECISION_DAYS = 30;
 /** Figures of the cockpit from the guarantee list (open ones) and the lines. */
 function figures(list, lines, today) {
  const open = list.filter(g => g.open), volume = {};
  open.forEach(g => { volume[g.currency] = (volume[g.currency] || 0) + Number(g.amount || 0); });
  const decisionLimit = new Date(today.getTime() + DECISION_DAYS * 86400000).toISOString().slice(0, 10);
  const decide = open.filter(g => g.decisionDate && g.decisionDate <= decisionLimit);
  const next = open.map(g => ({ g, date: [g.decisionDate, g.expiryDate].filter(Boolean).sort()[0], kind: g.decisionDate && (!g.expiryDate || g.decisionDate <= g.expiryDate) ? 'decision' : 'expiry' }))
   .filter(x => x.date).sort((a, b) => a.date.localeCompare(b.date)).slice(0, 8);
  return { openCount: open.length, volume, soon: open.filter(g => g.expiringSoon).length, decide: decide.length, requested: list.filter(g => g.status === 'REQUESTED').length, warned: (lines || []).filter(l => l.active && l.state && l.state !== 'OK').length, next };
 }
 globalThis.LcGuaranteeCockpit = { figures };
 if (typeof document === 'undefined') return;
 async function setup() {
  if (typeof can === 'function' && !can('GUARANTEE_VIEW')) return;
  let list; try { list = await json('/api/guarantees'); } catch { return; }
  const nav = el('button', tr('gc.nav', 'Übersicht')); nav.type = 'button'; nav.id = 'appNavGuaranteeCockpit'; nav.dataset.i18n = 'gc.nav';
  document.querySelector('#appNav nav').append(nav);
  const section = document.createElement('section'); section.id = 'guaranteeCockpitSection'; section.className = 'panel hidden';
  document.querySelector('main').append(section);
  async function draw() {
   try { list = await json('/api/guarantees'); } catch { return; }
   const overview = await json('/api/credit-lines').catch(() => ({ lines: [] })), f = figures(list, overview.lines, new Date());
   section.replaceChildren(el('h2', tr('gc.title', 'Garantien – Übersicht')), el('p', tr('gc.intro', 'Bestand, nächste Fristen und Auslastung der Linien auf einen Blick.')));
   const grid = el('div', undefined, 'grid');
   const card = (title, value, note, go) => { const c = el('div', undefined, 'card cockpit-card'); c.append(el('small', title), el('b', value), el('p', note || '')); if (go) { c.setAttribute('role', 'button'); c.tabIndex = 0; c.onclick = go; c.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); go(); } }; } grid.append(c); };
   const toList = () => document.querySelector('#appNavGuarantees')?.click();
   card(tr('gc.open', 'Offene Garantien'), String(f.openCount), Object.entries(f.volume).map(([c, v]) => money(v, c)).join(' · ') || '–', toList);
   card(tr('gc.soon', 'Laufen bald ab (90 Tage)'), String(f.soon), '', toList);
   card(tr('gc.decide', 'Entscheidung fällig (30 Tage)'), String(f.decide), tr('gc.decideNote', 'Verlängern oder zurückgeben'), toList);
   card(tr('gc.requested', 'Beantragt, noch nicht ausgestellt'), String(f.requested), '', toList);
   card(tr('gc.lines', 'Linien mit Warnung'), String(f.warned), '', () => document.querySelector('#appNavCreditLines')?.click());
   section.append(grid);
   section.append(el('h3', tr('gc.next', 'Nächste Fristen')));
   if (!f.next.length) section.append(el('p', tr('gc.noDates', 'Keine offenen Fristen.')));
   f.next.forEach(({ g, date, kind }) => {
    const row = el('div', undefined, 'gar-row'); const text = el('div');
    text.append(el('b', g.reference), el('span', ' · ' + (kind === 'decision' ? tr('gc.decisionOn', 'Entscheiden bis {date}', { date: day(date) }) : tr('gc.expiryOn', 'Läuft ab {date}', { date: day(date) })) + ' · ' + (g.bank || '').split('\n')[0]));
    const open = el('button', tr('gar.openFile', 'Akte öffnen'), 'secondary'); open.type = 'button';
    open.onclick = async () => { appNavigate('lcs', document.querySelector('#appNavGuarantees')); await show(g.id); };
    row.append(text, open); section.append(row);
   });
   const lines = (overview.lines || []).filter(l => l.active);
   if (lines.length) {
    section.append(el('h3', tr('gc.useTitle', 'Auslastung der Linien')));
    lines.forEach(l => { const row = el('div', undefined, 'gar-row'); const meter = document.createElement('meter'); meter.min = 0; meter.max = 100; meter.value = Math.min(100, l.percent); meter.title = l.percent + ' %';
     const text = el('div'); text.append(el('b', l.name), el('span', ' · ' + money(l.used, l.currency) + ' / ' + money(l.amount, l.currency) + ' (' + l.percent + ' %)')); row.append(text, meter); section.append(row); });
   }
  }
  nav.onclick = () => { appNavigate('guaranteeCockpit', nav); draw(); };
 }
 const timer = setInterval(() => { if (typeof appNavigate === 'function' && typeof json === 'function' && typeof currentUser !== 'undefined' && currentUser) { clearInterval(timer); setup(); } }, 300);
})();
