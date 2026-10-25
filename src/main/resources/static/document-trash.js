/* Trash of a file's documents: deleted documents stay restorable for 30 days. Adds a "Trash" button next to the upload button. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };

 /** Days left until the server removes a trashed document for good (never negative). */
 function daysLeft(deletedAt, now = Date.now(), retention = 30) {
  const left = retention - Math.floor((now - new Date(deletedAt).getTime()) / 86400000);
  return Math.max(0, Math.min(retention, left));
 }
 globalThis.LcTrash = { daysLeft };
 if (typeof document === 'undefined' || !document.body) return;

 const esc = value => String(value ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
 async function open() {
  if (typeof activeLc === 'undefined' || !activeLc) return;
  const lcId = activeLc.id;
  const dialog = document.createElement('dialog'); dialog.className = 'document-trash-dialog'; document.body.append(dialog); dialog.onclose = () => dialog.remove();
  const draw = items => {
   dialog.innerHTML = `<div class="dialoghead"><h2>${tr('tr.title', 'Papierkorb')}</h2><button type="button" data-close>${tr('common.close', 'Schließen')}</button></div><p>${tr('tr.note', 'Gelöschte Dokumente bleiben 30 Tage erhalten und können wiederhergestellt werden.')}</p>` +
    (items.length ? `<ul class="trash-list">${items.map(item => `<li><span><b>${esc(item.filename)}</b><small>${esc(item.documentType)} · ${tr('tr.left', 'noch {n} Tage', { n: daysLeft(item.deletedAt) })}</small></span><button type="button" class="secondary" data-restore="${esc(item.id)}">${tr('tr.restore', 'Wiederherstellen')}</button></li>`).join('')}</ul>` : `<p>${tr('tr.empty', 'Der Papierkorb ist leer.')}</p>`) + '<p data-error class="error" role="alert"></p>';
   dialog.querySelector('[data-close]').onclick = () => dialog.close();
   dialog.querySelectorAll('[data-restore]').forEach(button => button.onclick = async () => {
    button.disabled = true;
    try {
     await json(`/api/lcs/${encodeURIComponent(lcId)}/documents/${encodeURIComponent(button.dataset.restore)}/restore`, { method: 'POST' });
     dialog.close(); if (typeof show === 'function') await show(lcId);
    } catch (error) { dialog.querySelector('[data-error]').textContent = error.message; button.disabled = false; }
   });
  };
  dialog.showModal();
  try { draw(await json(`/api/lcs/${encodeURIComponent(lcId)}/documents/trash`)); } catch (error) { dialog.textContent = error.message; }
 }
 function enhance() {
  if (typeof can !== 'function' || !can('DOCUMENT_DELETE')) return;
  const upload = document.querySelector('#uploadBtn');
  if (!upload || document.querySelector('#documentTrashBtn')) return;
  const button = document.createElement('button'); button.type = 'button'; button.id = 'documentTrashBtn'; button.className = 'secondary';
  button.textContent = tr('tr.title', 'Papierkorb'); button.onclick = open; upload.before(button);
 }
 document.addEventListener('lc-language-changed', () => { const button = document.querySelector('#documentTrashBtn'); if (button) button.textContent = tr('tr.title', 'Papierkorb'); });
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; enhance(); }, 80); } }).observe(document.body, { childList: true, subtree: true });
 enhance();
})();
