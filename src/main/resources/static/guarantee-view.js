/* Guarantee files get their own overview instead of the letter-of-credit cockpit: bank, beneficiary, amount, term, status, documents. Guarantees are also kept out of the letter-of-credit list; the page "Garantien" is their entry point. Presentation only. */
(() => {
 if (typeof document === 'undefined' || !document.body || typeof show !== 'function') return;
 const tr = (key, de, values) => typeof tx === 'function' ? tx(key, de, values) : de;
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const first = value => String(value ?? '').split('\n')[0];
 const showBase = show;
 show = async function (id) {
  // a guarantee opens on its own page (no list of letters of credit), reached from the menu group Garantien
  const wanted = typeof data !== 'undefined' ? data.find(x => x.id === id) : null;
  if (wanted?.instrumentType === 'GUARANTEE' && typeof appNavigate === 'function') { try { appNavigate('guaranteeFile', document.querySelector('#appNavGuarantees')); } catch (ignored) { } }
  await showBase(id);
  document.body.classList.toggle('guarantee-file', typeof activeLc !== 'undefined' && activeLc?.instrumentType === 'GUARANTEE');
  if (typeof activeLc === 'undefined' || activeLc?.id !== id || activeLc.instrumentType !== 'GUARANTEE') return;
  try {
   const [g, docs] = await Promise.all([json('/api/guarantees/' + encodeURIComponent(id)), json('/api/lcs/' + encodeURIComponent(id) + '/documents').catch(() => [])]);
   const detail = document.querySelector('#detail');
   if (detail && !detail.querySelector('.guarantee-back')) {
    const back = document.createElement('button'); back.type = 'button'; back.className = 'secondary guarantee-back'; back.textContent = tr('gv.back', '← Zu den Garantien');
    back.onclick = () => document.querySelector('#appNavGuarantees')?.click(); detail.prepend(back);
   }
   const block = document.querySelector('#detail .lc-operational-cockpit');
   if (!block || activeLc?.id !== id) return;
   block.querySelector(':scope > h3')?.replaceChildren(tr('gv.title', 'Garantie-Übersicht'));
   const grid = block.querySelector(':scope > .grid'); if (!grid) return;
   const fmt = value => value ? (typeof fmtDate === 'function' ? fmtDate(value) : value) : '–';
   const term = g.daysToExpiry == null ? '' : g.daysToExpiry < 0 ? tr('gar.expired', 'abgelaufen seit {n} Tagen', { n: -g.daysToExpiry }) : tr('gar.daysLeft', 'noch {n} Tage', { n: g.daysToExpiry });
   const cards = [
    [tr('gv.bank', 'Bank'), first(g.bank) || '–', g.guaranteeType ? tr('gar.type.' + g.guaranteeType, g.guaranteeType) : '', 'overview'],
    [tr('gv.beneficiary', 'Begünstigter'), first(g.beneficiary) || '–', g.beneficiaryReference || '', 'overview'],
    [tr('gv.amount', 'Betrag'), (g.amount != null ? Number(g.amount).toLocaleString(tr('gv.locale', 'de-DE'), { minimumFractionDigits: 2 }) : '–') + ' ' + (g.currency || ''), g.bankReference ? tr('gar.bankRefLine', 'Referenz der Bank: {ref}', { ref: g.bankReference }) : '', 'overview'],
    [tr('gv.term', 'Laufzeit'), g.expiryDate ? tr('gar.until', 'bis {date}', { date: fmt(g.expiryDate) }) : tr('gar.noEnd', 'unbefristet'), term, 'deadlines'],
    [tr('gv.status', 'Status'), tr('gar.status.' + g.status, g.status), g.autoExtension ? tr('gar.autoShort', 'verlängert sich automatisch') : '', 'overview'],
    [tr('gv.documents', 'Dokumente'), String(docs.length), docs.some(d => d.documentType === 'GUARANTEE_COPY') ? tr('gv.copyFiled', 'Kopie der Garantie abgelegt') : tr('gv.copyMissing', 'Kopie der Garantie fehlt noch'), 'documents']
   ];
   grid.innerHTML = cards.map(([title, value, note, section]) => `<div class="card cockpit-card" role="button" tabindex="0" data-cockpit-section="${section}"><small>${esc(title)}</small><b>${esc(value)}</b><p>${esc(note)}</p></div>`).join('');
   grid.querySelectorAll('[data-cockpit-section]').forEach(card => { card.onclick = () => activateDossierSection(card.dataset.cockpitSection); card.onkeydown = event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); card.click(); } }; });
  } catch (ignored) { }
 };
})();
