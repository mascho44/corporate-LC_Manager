/* Global search and command palette (Ctrl+K / Cmd+K): finds pages of the menu and LC files (by reference, parties, banks and handler) and opens the chosen one.
   Searches the menu as it is on screen and the list that is already loaded, so it needs no server call. */
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
 /** Menu entries (`{label, run}`) whose label contains every word of `query`; labels that start with the query first. */
 function searchPages(items, query, limit = 4) {
  const words = String(query || '').toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return [];
  const scored = [];
  for (const item of items || []) {
   const label = String(item.label || '').toLowerCase();
   if (words.every(w => label.includes(w))) scored.push({ item, score: label.startsWith(words[0]) ? 0 : 1 });
  }
  return scored.sort((a, b) => a.score - b.score || a.item.label.localeCompare(b.item.label)).slice(0, limit).map(x => x.item);
 }
 globalThis.LcSearch = { search, searchPages };
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
 /** The menu entries the person can use right now (hidden ones, e.g. without permission, are left out). */
 function menuPages() {
  return [...document.querySelectorAll('#appNav nav button')]
   .filter(button => !button.classList.contains('hidden') && !button.hidden && !button.disabled)
   .map(button => { const copy = button.cloneNode(true); copy.querySelectorAll('.nav-badge').forEach(badge => badge.remove()); return { label: copy.textContent.replace(/\s+/g, ' ').trim(), run: () => button.click() }; })
   .filter(item => item.label);
 }
 function draw() {
  const input = dialog.querySelector('input');
  results = [...searchPages(menuPages(), input.value).map(page => ({ page })), ...search(typeof data === 'undefined' ? [] : data, input.value).map(lc => ({ lc }))];
  dialog.querySelector('ul').innerHTML = results.map((entry, i) => entry.page
   ? `<li role="option" data-i="${i}" aria-selected="${i === active}"><b>${esc(entry.page.label)}</b><span>${esc(tr('gs.page', 'Seite öffnen'))}</span></li>`
   : `<li role="option" data-i="${i}" aria-selected="${i === active}"><b>${esc(entry.lc.reference)}</b><span>${esc([entry.lc.beneficiary, entry.lc.applicant].filter(Boolean).join(' · '))}</span></li>`).join('');
  dialog.querySelector('small').textContent = !input.value.trim() ? tr('gs.hint', 'Seite, Referenz, Begünstigten, Antragsteller, Bank oder Bearbeiter eingeben') : results.length ? '' : tr('gs.none', 'Nichts Passendes gefunden.');
 }
 function choose(entry) {
  dialog.close();
  if (entry.page) { entry.page.run(); return; }
  document.querySelector('[data-app-section="lcs"]')?.click();
  if (typeof show === 'function') show(entry.lc.id);
 }
 function open() {
  if (typeof data === 'undefined') return;
  if (!dialog) build();
  const input = dialog.querySelector('input');
  input.setAttribute('aria-label', tr('gs.label', 'Seite oder Akte suchen')); input.placeholder = tr('gs.label', 'Seite oder Akte suchen');
  input.value = ''; active = 0; draw();
  if (!dialog.open) dialog.showModal();
  input.focus();
 }
 document.addEventListener('keydown', event => { if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); open(); } });
})();
