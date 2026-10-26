/* Saved views of the file list: the filters and the sort order under a name, stored on the server, private or shared with the tenant.
   The list itself is filtered by render(); this file only reads and sets the same controls and keeps the choice of views. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };
 /** The controls of the list and what they hold when nothing is chosen. */
 const CONTROLS = { search: ['#search', ''], traffic: ['#lcTrafficFilter', ''], sla: ['#lcSlaFilter', ''], owner: ['#lcOwnerFilter', ''], company: ['#lcCompanyFilter', ''], sort: ['#lcSort', 'priority'] };

 /** The current state of the list as a small object; empty and default values are left out. */
 function capture(read) {
  const filters = {};
  for (const [key, [selector, fallback]] of Object.entries(CONTROLS)) { const value = read(selector); if (value != null && value !== '' && value !== fallback) filters[key] = String(value); }
  const quick = read('quick'); if (quick) filters.quick = String(quick);
  return filters;
 }
 /** Sets every control to the saved value, or to its neutral value when the view does not mention it. */
 function restore(filters, write) {
  for (const [key, [selector, fallback]] of Object.entries(CONTROLS)) write(selector, filters[key] ?? fallback);
  write('quick', filters.quick ?? null);
 }
 globalThis.LcSavedViews = { capture, restore, CONTROLS };

 if (typeof document === 'undefined') return;
 const bar = document.querySelector('#lcSection .lc-filters');
 if (!bar) return;
 let views = [];
 const select = document.createElement('select'); select.id = 'lcViews';
 const mk = (id, cls) => { const b = document.createElement('button'); b.type = 'button'; b.id = id; b.className = cls; return b; };
 const saveBtn = mk('lcViewSave', 'secondary'), updateBtn = mk('lcViewUpdate', 'secondary'), deleteBtn = mk('lcViewDelete', 'secondary');
 bar.append(select, saveBtn, updateBtn, deleteBtn);

 const read = selector => selector === 'quick' ? globalThis.LcQuickFilter?.key : document.querySelector(selector)?.value;
 const write = (selector, value) => { if (selector === 'quick') { if (globalThis.LcQuickFilter) globalThis.LcQuickFilter.key = value; return; } const el = document.querySelector(selector); if (el) el.value = value; };
 const current = () => views.find(v => v.id === select.value);
 const can = permission => typeof globalThis.can === 'function' ? globalThis.can(permission) : false;

 function labels() {
  saveBtn.textContent = tr('sv.save', 'Ansicht speichern'); updateBtn.textContent = tr('sv.update', 'Aktualisieren'); deleteBtn.textContent = tr('sv.delete', 'Löschen');
  select.setAttribute('aria-label', tr('sv.views', 'Gespeicherte Ansichten'));
 }
 function draw(keep) {
  select.replaceChildren(new Option(tr('sv.none', 'Ansichten …'), ''));
  for (const v of views) select.append(new Option(v.name + (v.shared ? ' · ' + (v.mine ? tr('sv.shared', 'geteilt') : tr('sv.sharedBy', 'von {user}', { user: v.owner })) : ''), v.id));
  if (keep && views.some(v => v.id === keep)) select.value = keep;
  select.hidden = views.length === 0; buttons();
 }
 function buttons() {
  const v = current();
  updateBtn.hidden = !v || !v.mine; deleteBtn.hidden = !v || !(v.mine || (v.shared && can('SETTINGS_MANAGE')));
 }
 async function load(keep) { try { views = await json('/api/saved-views'); } catch (ignored) { views = []; } draw(keep); }
 const send = (url, method, body) => json(url, { method, headers: { 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
 const redraw = () => { if (typeof globalThis.render === 'function') globalThis.render(); };

 select.onchange = () => { const v = current(); buttons(); if (v) { restore(v.filters, write); redraw(); } };

 let dialog;
 function ensureDialog() {
  if (dialog) return dialog;
  dialog = document.createElement('dialog'); dialog.id = 'savedViewDialog';
  dialog.innerHTML = '<form method="dialog" class="saved-view-form"><h3></h3><label><span class="sv-name-label"></span><input name="name" maxlength="60" required autocomplete="off"></label><label class="check-label"><input type="checkbox" name="shared"> <span class="sv-share-label"></span></label><p class="members-note" role="status"></p><div class="actions"><button type="button" class="secondary sv-cancel"></button><button type="submit" class="sv-ok"></button></div></form>';
  document.body.append(dialog); return dialog;
 }
 saveBtn.onclick = () => {
  const d = ensureDialog(), form = d.querySelector('form');
  d.querySelector('h3').textContent = tr('sv.saveTitle', 'Ansicht speichern');
  d.querySelector('.sv-name-label').textContent = tr('sv.name', 'Name'); d.querySelector('.sv-share-label').textContent = tr('sv.share', 'Mit allen im Mandanten teilen');
  d.querySelector('.sv-cancel').textContent = tr('sv.cancel', 'Abbrechen'); d.querySelector('.sv-ok').textContent = tr('sv.ok', 'Speichern');
  form.elements.name.value = ''; form.elements.shared.checked = false; d.querySelector('p').textContent = '';
  d.querySelector('.sv-cancel').onclick = () => d.close();
  form.onsubmit = async event => {
   event.preventDefault();
   try {
    const saved = await send('/api/saved-views', 'POST', { name: form.elements.name.value, shared: form.elements.shared.checked, filters: capture(read) });
    d.close(); await load(saved.id);
   } catch (error) { d.querySelector('p').textContent = error.message; }
  };
  d.showModal(); form.elements.name.focus();
 };
 updateBtn.onclick = async () => {
  const v = current(); if (!v) return;
  try { const saved = await send('/api/saved-views/' + v.id, 'PUT', { name: v.name, shared: v.shared, filters: capture(read) }); await load(saved.id); } catch (error) { alert(error.message); }
 };
 deleteBtn.onclick = async () => {
  const v = current(); if (!v || !confirm(tr('sv.deleteConfirm', 'Die Ansicht „{name}“ löschen?', { name: v.name }))) return;
  try { await send('/api/saved-views/' + v.id, 'DELETE'); await load(''); } catch (error) { alert(error.message); }
 };

 labels(); document.addEventListener('lc-language-changed', () => { labels(); draw(select.value); });
 let tries = 0;
 const wait = setInterval(() => { tries++; if (typeof currentUser !== 'undefined' && currentUser) { clearInterval(wait); load(''); } else if (tries > 60) clearInterval(wait); }, 1000);
})();
