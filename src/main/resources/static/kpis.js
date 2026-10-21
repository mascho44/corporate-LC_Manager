/* Key figures above the file list: how many files are open, how much volume, and what needs attention soon. Computed from the loaded list. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const OPEN = lc => lc.status !== 'CLOSED' && lc.status !== 'EXPIRED';
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? `{${n}}`)); };
 const days = (date, today) => date ? Math.round((new Date(date + 'T00:00:00') - new Date(today + 'T00:00:00')) / 86400000) : null;

 /** The figures for the given files; today as yyyy-mm-dd. Closed and expired files count only as such. */
 function kpis(list, today, horizon = 14) {
  const open = (list || []).filter(OPEN), volume = {};
  open.forEach(lc => { if (lc.amount != null && lc.currency) volume[lc.currency] = (volume[lc.currency] || 0) + Number(lc.amount); });
  const within = field => open.filter(lc => { const d = days(lc[field], today); return d != null && d >= 0 && d <= horizon; }).length;
  return {
   open: open.length, closed: (list || []).length - open.length, volume,
   expiring: within('expiryDate'), shipping: within('latestShipmentDate'),
   overdueExpiry: open.filter(lc => { const d = days(lc.expiryDate, today); return d != null && d < 0; }).length,
   followUps: open.filter(lc => { const d = days(lc.followUpDate, today); return d != null && d <= 0; }).length,
   unassigned: open.filter(lc => !lc.assignedTo).length,
   waiting: open.filter(lc => lc.status === 'WAITING_FOR_CUSTOMER').length, horizon
  };
 }

 /** Quick filters of the key figures: which files a tile stands for. "attention" is the combined view for what needs action soon. */
 function matches(key, lc, today, horizon = 14) {
  if (!key) return true;
  if (!OPEN(lc) && key !== 'all') return false;
  const expiry = days(lc.expiryDate, today), shipment = days(lc.latestShipmentDate, today), followUp = days(lc.followUpDate, today);
  switch (key) {
   case 'open': return true;
   case 'expiring': return expiry != null && expiry >= 0 && expiry <= horizon;
   case 'shipping': return shipment != null && shipment >= 0 && shipment <= horizon;
   case 'overdue': return expiry != null && expiry < 0;
   case 'followUps': return followUp != null && followUp <= 0;
   case 'unassigned': return !lc.assignedTo;
   case 'waiting': return lc.status === 'WAITING_FOR_CUSTOMER';
   case 'attention': return (expiry != null && expiry <= 7) || (shipment != null && shipment >= 0 && shipment <= 7) || (followUp != null && followUp <= 0) || lc.dossierStatus?.status === 'RED';
   default: return true;
  }
 }
 const VIEWS = { all: { quick: null, owner: '' }, mine: { quick: null, owner: 'mine' }, soon: { quick: 'expiring', owner: '' }, attention: { quick: 'attention', owner: '' } };
 /** Readable name of a quick filter, for the "Filtered: …" note. */
 const filterName = (key, horizon = 14) => ({
  open: () => tr('kpi.open', 'Offene Akten'), expiring: () => tr('kpi.expiring', 'Ablauf in {n} Tagen', { n: horizon }), shipping: () => tr('kpi.shipping', 'Verladung in {n} Tagen', { n: horizon }),
  overdue: () => tr('kpi.overdue', 'Ablauf überschritten'), followUps: () => tr('kpi.followUps', 'Wiedervorlage fällig'), unassigned: () => tr('kpi.unassigned', 'Nicht zugewiesen'),
  waiting: () => tr('kpi.waiting', 'Wartet auf Kunde'), attention: () => tr('kpi.viewAttention', 'Braucht Aufmerksamkeit')
 })[key]?.() || key;
 globalThis.LcQuickFilter = { key: null };

 const money = (amount, currency) => `${new Intl.NumberFormat('de-DE', { maximumFractionDigits: 0 }).format(amount)} ${currency}`;
 /** The strip: view chips, then one tile per figure; tiles that stand for a set of files are clickable and show whether they are the active filter. */
 function html(k, state = {}) {
  const active = state.quick || null, owner = state.owner || '';
  const tile = (label, value, hot, hint, key) => key
   ? `<div class="kpi kpi-filter${hot ? ' kpi-hot' : ''}" role="button" tabindex="0" data-kpi="${key}" aria-pressed="${active === key}" title="${esc((hint ? hint + ' · ' : '') + tr('kpi.clickHint', 'Klicken, um die Liste zu filtern'))}"><b>${esc(value)}</b><small>${esc(label)}</small></div>`
   : `<div class="kpi${hot ? ' kpi-hot' : ''}" title="${esc(hint || '')}"><b>${esc(value)}</b><small>${esc(label)}</small></div>`;
  const volume = Object.entries(k.volume).sort((a, b) => b[1] - a[1]).map(([currency, amount]) => money(amount, currency)).join(' · ') || '–';
  const current = Object.keys(VIEWS).find(name => (VIEWS[name].quick || null) === active && VIEWS[name].owner === owner) || null;
  const chip = (name, label) => `<button type="button" class="secondary kpi-view" data-kpi-view="${name}" aria-pressed="${current === name}">${esc(label)}</button>`;
  const views = `<div class="kpi-views" role="group" aria-label="${esc(tr('kpi.views', 'Ansichten'))}">${chip('all', tr('kpi.viewAll', 'Alle Akten'))}${chip('mine', tr('kpi.viewMine', 'Meine Akten'))}${chip('soon', tr('kpi.viewSoon', 'Läuft bald ab'))}${chip('attention', tr('kpi.viewAttention', 'Braucht Aufmerksamkeit'))}` +
   (active && !current ? `<span class="kpi-active">${esc(tr('kpi.filtered', 'Gefiltert: {name}', { name: filterName(active, k.horizon) }))}</span><button type="button" class="secondary kpi-view" data-kpi-reset>${esc(tr('kpi.reset', 'Filter zurücksetzen'))}</button>` : '') + '</div>';
  return views + tile(tr('kpi.open', 'Offene Akten'), k.open, false, tr('kpi.closed', '{n} abgeschlossen oder abgelaufen', { n: k.closed }), 'open') +
   tile(tr('kpi.volume', 'Volumen offen'), volume, false, tr('kpi.volumeHint', 'Summe der Akkreditivbeträge offener Akten je Währung')) +
   tile(tr('kpi.expiring', 'Ablauf in {n} Tagen', { n: k.horizon }), k.expiring, k.expiring > 0, '', 'expiring') + tile(tr('kpi.shipping', 'Verladung in {n} Tagen', { n: k.horizon }), k.shipping, k.shipping > 0, '', 'shipping') +
   tile(tr('kpi.overdue', 'Ablauf überschritten'), k.overdueExpiry, k.overdueExpiry > 0, tr('kpi.overdueHint', 'Offene Akten, deren Ablaufdatum vorbei ist'), 'overdue') + tile(tr('kpi.followUps', 'Wiedervorlage fällig'), k.followUps, k.followUps > 0, '', 'followUps') +
   tile(tr('kpi.unassigned', 'Nicht zugewiesen'), k.unassigned, false, '', 'unassigned') + (k.waiting ? tile(tr('kpi.waiting', 'Wartet auf Kunde'), k.waiting, false, '', 'waiting') : '');
 }
 globalThis.LcKpis = { kpis, html, matches, VIEWS };
 if (typeof document === 'undefined' || !document.body) return;

 function render() {
  const list = document.querySelector('#list');
  if (!list || typeof data === 'undefined' || !Array.isArray(data)) return;
  let strip = document.querySelector('#kpiStrip');
  if (!strip) { strip = document.createElement('section'); strip.id = 'kpiStrip'; strip.className = 'kpi-strip'; strip.setAttribute('aria-label', tr('kpi.label', 'Kennzahlen')); list.before(strip); }
  strip.hidden = data.length === 0;
  const today = new Date().toISOString().slice(0, 10), content = html(kpis(data, today), { quick: globalThis.LcQuickFilter.key, owner: document.querySelector('#lcOwnerFilter')?.value || '' });
  if (strip.dataset.sig !== content) { strip.dataset.sig = content; strip.innerHTML = content; }
 }
 /** Applies a quick filter and/or the owner filter and redraws the list and the strip. */
 function apply(quick, owner) {
  globalThis.LcQuickFilter.key = quick || null;
  const select = document.querySelector('#lcOwnerFilter');
  if (select && owner !== undefined) select.value = owner;
  if (typeof globalThis.render === 'function') globalThis.render(); else if (typeof render === 'function') render();
  try { kpiRender(); } catch (ignored) { }
 }
 const kpiRender = () => render();
 function handle(event) {
  const target = event.target.closest?.('[data-kpi],[data-kpi-view],[data-kpi-reset]');
  if (!target || !target.closest('#kpiStrip')) return;
  if (event.type === 'keydown' && event.key !== 'Enter' && event.key !== ' ') return;
  event.preventDefault();
  if (target.dataset.kpiReset !== undefined) return apply(null, '');
  if (target.dataset.kpiView) { const view = VIEWS[target.dataset.kpiView]; return apply(view.quick, view.owner); }
  const key = target.dataset.kpi;
  apply(globalThis.LcQuickFilter.key === key ? null : key);
 }
 document.addEventListener('click', handle);
 document.addEventListener('keydown', handle);
 // The buttons of the corporate workflow carry their text from the moment they were added; on a language change they are rebuilt by their observers.
 document.addEventListener('lc-language-changed', () => {
  document.querySelectorAll('#documentSetBtn,#submissionPackageBtn,#draftCheckBtn,#amendmentRequestBtn,#copyLcBtn,[data-lc-tool]').forEach(button => button.remove());
  document.querySelector('#generateDocumentDialog')?.remove(); // built once with its texts; rebuilt on the next use
  try { render(); } catch (ignored) { }
 });
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; try { render(); } catch (ignored) { } }, 120); } }).observe(document.body, { childList: true, subtree: true });
 render();
})();
