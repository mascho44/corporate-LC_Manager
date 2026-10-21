/* Drag and drop on an open LC file: dropping files on the file view opens the upload dialog with those files already chosen. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };

 /** True when the drag carries files (not text or a link). */
 const hasFiles = event => [...(event.dataTransfer?.types || [])].includes('Files');
 globalThis.LcDragDrop = { hasFiles };
 if (typeof document === 'undefined' || !document.body) return;

 const detail = () => document.querySelector('#detail');
 const allowed = () => typeof activeLc !== 'undefined' && activeLc && typeof can === 'function' && can('DOCUMENT_UPLOAD') && !detail()?.classList.contains('hidden');
 let depth = 0;
 const mark = on => { const el = detail(); if (!el) return; el.classList.toggle('file-drop-over', on); if (on) el.dataset.dropLabel = tr('dd.label', 'Dateien hier ablegen, um sie zur Akte hochzuladen'); };
 document.addEventListener('dragenter', event => { if (hasFiles(event) && allowed() && event.target.closest?.('#detail')) { depth++; mark(true); } });
 document.addEventListener('dragleave', event => { if (event.target.closest?.('#detail') && depth > 0 && --depth === 0) mark(false); });
 document.addEventListener('dragover', event => { if (hasFiles(event) && allowed() && event.target.closest?.('#detail')) event.preventDefault(); });
 document.addEventListener('drop', event => {
  if (!event.target.closest?.('#detail') || !hasFiles(event) || !allowed()) return;
  event.preventDefault(); depth = 0; mark(false);
  const files = event.dataTransfer.files, input = document.querySelector('#uploadForm')?.elements.file;
  if (!files.length || !input) return;
  document.querySelector('#uploadBtn')?.click();
  try { input.files = files; input.dispatchEvent(new Event('change', { bubbles: true })); } catch { /* the dialog stays open for choosing manually */ }
 });
})();
