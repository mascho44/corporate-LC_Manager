/* Small first-page thumbnails for PDF documents in the dossier and in the inbox. Loaded lazily by the browser; a click opens the page larger. */
(() => {
 const isPdf = name => /\.pdf$/i.test(String(name || ''));
 const documentThumbUrl = id => `/api/documents/${encodeURIComponent(id)}/pages/1/preview?size=thumb`;
 const documentLargeUrl = id => `/api/documents/${encodeURIComponent(id)}/pages/1/preview`;
 const inboxThumbUrl = id => `/api/inbox/${encodeURIComponent(id)}/pages/1/preview?enlarged=false`;
 const inboxLargeUrl = id => `/api/inbox/${encodeURIComponent(id)}/pages/1/preview?enlarged=true`;
 globalThis.LcThumbnails = { isPdf, documentThumbUrl, documentLargeUrl, inboxThumbUrl, inboxLargeUrl };
 if (typeof document === 'undefined' || !document.body) return;

 function enlarge(url, title) {
  const dialog = document.createElement('dialog'); dialog.className = 'thumb-large-dialog';
  const head = document.createElement('div'); head.className = 'dialoghead';
  const h = document.createElement('h2'); h.textContent = title; const close = document.createElement('button'); close.type = 'button'; close.textContent = 'Schließen';
  head.append(h, close);
  const img = document.createElement('img'); img.alt = 'Seite 1 von ' + title; img.src = url;
  dialog.append(head, img); document.body.append(dialog);
  close.onclick = () => dialog.close(); dialog.onclose = () => dialog.remove(); dialog.showModal();
 }
 function thumb(src, large, title) {
  const button = document.createElement('button'); button.type = 'button'; button.className = 'doc-thumb'; button.title = 'Seite 1 vergrößern';
  const img = document.createElement('img'); img.loading = 'lazy'; img.decoding = 'async'; img.alt = 'Vorschau ' + title; img.src = src;
  img.onerror = () => button.remove();
  button.append(img);
  button.onclick = event => { event.preventDefault(); event.stopPropagation(); enlarge(large, title); };
  return button;
 }
 function enhance() {
  document.querySelectorAll('.document-row:not([data-thumb])').forEach(row => {
   row.dataset.thumb = '1';
   const name = row.querySelector('.document-download b')?.textContent, id = (row.querySelector('[data-rule-facts]') || row.querySelector('[data-document-edit]'))?.dataset.ruleFacts
    || row.querySelector('[data-document-edit]')?.dataset.documentEdit;
   if (!id || !isPdf(name)) return;
   row.classList.add('has-thumb');
   row.querySelector('.file-icon')?.after(thumb(documentThumbUrl(id), documentLargeUrl(id), name));
  });
  document.querySelectorAll('.inbox-item:not([data-thumb])').forEach(article => {
   article.dataset.thumb = '1';
   const name = article.querySelector('.inbox-item-head b')?.textContent, id = article.dataset.inboxId;
   if (!id || !isPdf(name)) return;
   article.classList.add('has-thumb');
   article.querySelector('.inbox-item-head')?.prepend(thumb(inboxThumbUrl(id), inboxLargeUrl(id), name));
  });
 }
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; enhance(); }, 50); } }).observe(document.body, { childList: true, subtree: true });
 enhance();
})();
