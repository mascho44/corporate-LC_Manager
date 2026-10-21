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

 const money = (amount, currency) => `${new Intl.NumberFormat('de-DE', { maximumFractionDigits: 0 }).format(amount)} ${currency}`;
 function html(k) {
  const tile = (label, value, hot, hint) => `<div class="kpi${hot ? ' kpi-hot' : ''}" title="${esc(hint || '')}"><b>${esc(value)}</b><small>${esc(label)}</small></div>`;
  const volume = Object.entries(k.volume).sort((a, b) => b[1] - a[1]).map(([currency, amount]) => money(amount, currency)).join(' · ') || '–';
  return tile(tr('kpi.open', 'Offene Akten'), k.open, false, tr('kpi.closed', '{n} abgeschlossen oder abgelaufen', { n: k.closed })) + tile(tr('kpi.volume', 'Volumen offen'), volume, false, tr('kpi.volumeHint', 'Summe der Akkreditivbeträge offener Akten je Währung')) +
   tile(tr('kpi.expiring', 'Ablauf in {n} Tagen', { n: k.horizon }), k.expiring, k.expiring > 0) + tile(tr('kpi.shipping', 'Verladung in {n} Tagen', { n: k.horizon }), k.shipping, k.shipping > 0) +
   tile(tr('kpi.overdue', 'Ablauf überschritten'), k.overdueExpiry, k.overdueExpiry > 0, tr('kpi.overdueHint', 'Offene Akten, deren Ablaufdatum vorbei ist')) + tile(tr('kpi.followUps', 'Wiedervorlage fällig'), k.followUps, k.followUps > 0) +
   tile(tr('kpi.unassigned', 'Nicht zugewiesen'), k.unassigned, false) + (k.waiting ? tile(tr('kpi.waiting', 'Wartet auf Kunde'), k.waiting, false) : '');
 }
 globalThis.LcKpis = { kpis, html };
 if (typeof document === 'undefined' || !document.body) return;

 function render() {
  const list = document.querySelector('#list');
  if (!list || typeof data === 'undefined' || !Array.isArray(data)) return;
  let strip = document.querySelector('#kpiStrip');
  if (!strip) { strip = document.createElement('section'); strip.id = 'kpiStrip'; strip.className = 'kpi-strip'; strip.setAttribute('aria-label', tr('kpi.label', 'Kennzahlen')); list.before(strip); }
  strip.hidden = data.length === 0;
  const today = new Date().toISOString().slice(0, 10), content = html(kpis(data, today));
  if (strip.dataset.sig !== content) { strip.dataset.sig = content; strip.innerHTML = content; }
 }
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
