/* Advice from the bank (cover letter + credit text): read the credit text of an inbox PDF, show what was found, and create the LC file from it. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const date = value => value ? new Date(value + 'T00:00:00').toLocaleDateString('de-DE') : '–';
 const money = (amount, currency) => amount == null ? '–' : `${Number(amount).toLocaleString('de-DE', { minimumFractionDigits: 2 })} ${currency || ''}`.trim();
 function previewHtml(result) {
  if (!result.creditTextFound) return '<p role="alert">Im Dokument wurde kein Akkreditivtext erkannt. Der Avis muss neben dem Anschreiben den Text des Akkreditivs enthalten (Felder wie 20, 31D, 32B, 50, 59). Bitte die Datei prüfen oder die Akte manuell anlegen.</p>';
  const c = result.credit;
  const messages = [...(c.errors || []).map(m => `<li class="avis-error">${esc(m)}</li>`), ...(c.warnings || []).map(m => `<li>${esc(m)}</li>`)].join('');
  const docs = (c.requiredDocuments || []).map(d => `<li>${esc(d)}</li>`).join('');
  return `<dl class="avis-facts"><dt>Referenz</dt><dd>${esc(c.reference || '–')}</dd><dt>Auftraggeber (Applicant)</dt><dd>${esc(c.applicant || '–')}</dd><dt>Begünstigter</dt><dd>${esc(c.beneficiary || '–')}</dd>` +
   `<dt>Betrag</dt><dd>${esc(money(c.amount, c.currency))}</dd><dt>Ablauf</dt><dd>${esc(date(c.expiryDate))}${c.expiryPlace ? ' · ' + esc(c.expiryPlace) : ''}</dd></dl>` +
   (docs ? `<h3>Geforderte Dokumente (${(c.requiredDocuments || []).length})</h3><ul class="avis-docs">${docs}</ul>` : '<p>Keine Dokumentenanforderungen erkannt.</p>') +
   (messages ? `<ul class="avis-messages" role="status">${messages}</ul>` : '') +
   `<p class="avis-note"><small>Die Angaben stammen aus dem Text des Avis und sind unbestätigt. Bitte mit dem Original vergleichen; beim Anlegen werden alle Felder des Akkreditivtextes übernommen.</small></p>`;
 }
 globalThis.LcAvisCase = { previewHtml };
 if (typeof document === 'undefined' || !document.body) return;

 async function open(id, name) {
  const dialog = document.createElement('dialog'); dialog.className = 'wide-dialog avis-dialog'; document.body.append(dialog); dialog.onclose = () => dialog.remove();
  dialog.innerHTML = `<div class="dialoghead"><h2>Avis einlesen</h2><button type="button" data-close>Schließen</button></div><p role="status">${esc(name)} wird gelesen …</p>`;
  dialog.querySelector('[data-close]').onclick = () => dialog.close(); dialog.showModal();
  try {
   const result = await json(`/api/inbox/${encodeURIComponent(id)}/avis-preview`);
   const ok = result.creditTextFound && result.credit?.valid;
   dialog.innerHTML = `<div class="dialoghead"><h2>Avis einlesen</h2><button type="button" data-close>Schließen</button></div><p>${esc(name)}</p>${previewHtml(result)}` +
    `<p data-error role="alert"></p><div class="dialog-actions"><button type="button" data-create${ok ? '' : ' disabled'}>LC-Akte aus Avis anlegen</button></div>`;
   dialog.querySelector('[data-close]').onclick = () => dialog.close();
   dialog.querySelector('[data-create]').onclick = async event => {
    const button = event.currentTarget; button.disabled = true;
    try {
     const created = await json(`/api/inbox/${encodeURIComponent(id)}/avis-case`, { method: 'POST' });
     dialog.close();
     const message = document.querySelector('#inboxMessage'); if (message) message.textContent = `LC-Akte „${created.reference}" aus dem Avis angelegt; der Avis ist als Dokument abgelegt.`;
     if (typeof load === 'function') await load(); if (typeof loadInbox === 'function') await loadInbox();
    } catch (error) { dialog.querySelector('[data-error]').textContent = error.message; button.disabled = false; }
   };
  } catch (error) { dialog.querySelector('[role=status]').textContent = error.message; }
 }

 function enhance() {
  if (typeof can !== 'function' || !can('LC_EDIT') || !can('DOCUMENT_UPLOAD')) return;
  document.querySelectorAll('.inbox-item:not([data-avis])').forEach(article => {
   if (article.dataset.pending === 'true') return;
   article.dataset.avis = '1';
   const name = article.querySelector('.inbox-item-head b')?.textContent || '';
   if (!/\.pdf$/i.test(name) || article.dataset.inboxSource) return;
   const button = document.createElement('button'); button.type = 'button'; button.className = 'secondary avis-open'; button.textContent = 'Als Avis einlesen …';
   button.title = 'Akkreditivtext des Avis lesen und daraus eine LC-Akte anlegen';
   button.onclick = () => open(article.dataset.inboxId, name);
   (article.querySelector('.inbox-split-action') || article.querySelector('.inbox-item-head'))?.after(button);
  });
 }
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; enhance(); }, 80); } }).observe(document.body, { childList: true, subtree: true });
 enhance();
})();
