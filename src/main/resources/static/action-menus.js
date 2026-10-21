/* Groups the document buttons of a file into two menus ("Create", "Check & submit") so the row stays short, and moves "Copy as template" to the file header.
   The buttons themselves are still made by their own scripts; this one only arranges them, again whenever the page changes. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? `{${n}}`)); };

 /** The menus and the buttons that belong in them, in display order. */
 const GROUPS = () => [
  { id: 'create', label: tr('am.create', 'Erzeugen'), items: ['[data-doc-create]', '#documentSetBtn'] },
  { id: 'check', label: tr('am.checkSubmit', 'Prüfen & einreichen'), items: ['#draftCheckBtn', '#submissionPackageBtn', '#amendmentRequestBtn'] }
 ];

 globalThis.LcActionMenus = { GROUPS };
 if (typeof document === 'undefined' || !document.body) return;

 function menuFor(group, before) {
  let menu = document.querySelector(`#actionMenu-${group.id}`);
  if (!menu) {
   menu = document.createElement('div'); menu.id = `actionMenu-${group.id}`; menu.className = 'action-menu';
   menu.innerHTML = '<button type="button" class="secondary action-menu-toggle" aria-haspopup="true" aria-expanded="false"></button><div class="action-menu-list" role="group" hidden></div>';
   menu.querySelector('.action-menu-toggle').onclick = event => { event.stopPropagation(); toggle(menu); };
   before.before(menu);
  }
  menu.querySelector('.action-menu-toggle').textContent = group.label;
  return menu;
 }
 function toggle(menu, open) {
  const list = menu.querySelector('.action-menu-list'), button = menu.querySelector('.action-menu-toggle');
  const next = open === undefined ? list.hidden : open;
  document.querySelectorAll('.action-menu-list').forEach(other => { if (other !== list) { other.hidden = true; other.parentElement.querySelector('.action-menu-toggle').setAttribute('aria-expanded', 'false'); } });
  list.hidden = !next; button.setAttribute('aria-expanded', String(next));
 }

 function arrange() {
  const upload = document.querySelector('#uploadBtn');
  if (!upload) return;
  for (const group of GROUPS()) {
   const found = group.items.flatMap(selector => [...document.querySelectorAll(selector)]);
   let menu = document.querySelector(`#actionMenu-${group.id}`);
   if (!found.length) { menu?.remove(); continue; }
   menu = menuFor(group, upload);
   const list = menu.querySelector('.action-menu-list');
   found.forEach((button, index) => { button.classList.add('action-menu-item'); if (list.children[index] !== button) list.insertBefore(button, list.children[index] || null); });
  }
  // "Copy as template" is an action on the whole file, so it sits with Edit and Close.
  const copy = document.querySelector('#copyLcBtn'), edit = document.querySelector('#editLc');
  if (copy && edit && copy.nextElementSibling !== edit) edit.before(copy);
 }
 function relabel() {
  document.querySelectorAll('[data-doc-create]').forEach(button => {
   const config = typeof generatedDocumentTypes !== 'undefined' ? generatedDocumentTypes[button.dataset.docCreate] : null;
   if (config) button.textContent = '+ ' + config.title;
  });
  for (const group of GROUPS()) { const toggleButton = document.querySelector(`#actionMenu-${group.id} .action-menu-toggle`); if (toggleButton) toggleButton.textContent = group.label; }
 }
 const closeAll = () => document.querySelectorAll('.action-menu').forEach(menu => toggle(menu, false));
 document.addEventListener('click', event => { const inside = event.target.closest?.('.action-menu'); if (!inside) closeAll(); else if (event.target.closest('.action-menu-list button')) setTimeout(closeAll, 0); });
 document.addEventListener('keydown', event => { if (event.key === 'Escape') closeAll(); });
 document.addEventListener('lc-language-changed', () => { try { relabel(); } catch (ignored) { } });

  let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; try { arrange(); } catch (ignored) { } }, 90); } }).observe(document.body, { childList: true, subtree: true });
 arrange();
})();
