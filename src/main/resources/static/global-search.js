/* Global search (Ctrl+K / Cmd+K): finds LC files by reference, parties, banks and handler and opens the chosen one.
   Searches the list that is already loaded, so it needs no server call. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };

 /** Matches of `query` in the LC list: every word must occur in one of the searched fields; best matches (reference first) on top. */
 function search(list, query, limit = 8) {
  const words = String(query || '').toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return [];
  const scored = [];
  for (const lc of list || []) {
   const ref = String(lc.reference || '').toLowerCase();
   const text = [lc.reference, lc.ownBankReference, lc.foreignBankReference, lc.applicant, lc.beneficiary, lc.issuingBank, lc.advisingBank, lc.assignedTo].join(' ').toLowerCase();
   if (!words.every(w => text.includes(w))) continue;
   scored.push({ lc, score: words.every(w => ref.startsWith(w)) ? 0 : words.every(w => ref.includes(w)) ? 1 : 2 });
  }
  return scored.sort((a, b) => a.score - b.score || String(a.lc.reference).localeCompare(String(b.lc.reference))).slice(0, limit).map(x => x.lc);
 }
 globalThis.LcSearch = { search };
 if (typeof document === 'undefined' || !document.body) return;

 let dialog = null, active = 0, results = [];
 const esc = value => String(value ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
 function build() {
  dialog = document.createElement('dialog'); dialog.id = 'globalSearch'; dialog.className = 'global-search';
  dialog.innerHTML = '<input type="search" autocomplete="off" aria-label=""><ul role="listbox"></ul><small></small>';
  document.body.append(dialog);
  const input = dialog.querySelector('input');
  input.oninput = () => { active = 0; draw(); };
  input.onkeydown = event => {
   if (event.key === 'ArrowDown') { active = Math.min(active + 1, results.length - 1); draw(); event.preventDefault(); }
   else if (event.key === 'ArrowUp') { active = Math.max(active - 1, 0); draw(); event.preventDefault(); }
   else if (event.key === 'Enter' && results[active]) { choose(results[active]); event.preventDefault(); }
  };
  dialog.addEventListener('click', event => { if (event.target === dialog) dialog.close(); });
  dialog.querySelector('ul').addEventListener('click', event => { const item = event.target.closest('[data-i]'); if (item) choose(results[Number(item.dataset.i)]); });
 }
 function draw() {
  const input = dialog.querySelector('input');
  results = search(typeof data === 'undefined' ? [] : data, input.value);
  dialog.querySelector('ul').innerHTML = results.map((lc, i) => `<li role="option" data-i="${i}" aria-selected="${i === active}"><b>${esc(lc.reference)}</b><span>${esc([lc.beneficiary, lc.applicant].filter(Boolean).join(' · '))}</span></li>`).join('');
  dialog.querySelector('small').textContent = !input.value.trim() ? tr('gs.hint', 'Referenz, Begünstigten, Antragsteller, Bank oder Bearbeiter eingeben') : results.length ? '' : tr('gs.none', 'Keine passende Akte gefunden.');
 }
 function choose(lc) {
  dialog.close();
  document.querySelector('[data-app-section="lcs"]')?.click();
  if (typeof show === 'function') show(lc.id);
 }
 function open() {
  if (typeof data === 'undefined') return;
  if (!dialog) build();
  const input = dialog.querySelector('input');
  input.setAttribute('aria-label', tr('gs.label', 'Akte suchen')); input.placeholder = tr('gs.label', 'Akte suchen');
  input.value = ''; active = 0; draw();
  if (!dialog.open) dialog.showModal();
  input.focus();
 }
 document.addEventListener('keydown', event => { if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); open(); } });
})();
