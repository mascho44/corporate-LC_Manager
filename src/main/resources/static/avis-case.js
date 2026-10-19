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
 /** Choice of the annex file (credit text) that belongs to the letter; empty when the inbox holds no other file with a credit text. */
 function annexHtml(partners, selected) {
  if (!partners?.length) return '';
  const options = partners.map(p => `<option value="${esc(p.id)}"${p.id === selected ? ' selected' : ''}>${esc(p.filename)} · ${esc(p.reason)}</option>`).join('');
  return `<label class="avis-annex">Anlage mit dem Akkreditivtext<select data-annex><option value="">keine – nur dieses Dokument</option>${options}</select></label>` +
   (selected ? '<p class="avis-note"><small>Der Akkreditivtext wird aus der Anlage gelesen. Das Schreiben und die Anlage werden beide in der neuen Akte abgelegt.</small></p>' : '');
 }
 const COVER_FIELDS = [['reference', 'Akkreditivnummer *'], ['applicant', 'Auftraggeber (Applicant)'], ['beneficiary', 'Begünstigter'], ['amount', 'Betrag'], ['currency', 'Währung'], ['expiryDate', 'Gültig bis'], ['expiryPlace', 'Ort'], ['issuingBank', 'Eröffnende Bank'], ['ownBankReference', 'Referenz eigene Bank'], ['foreignBankReference', 'Referenz Fremdbank']];
 /** Advice without credit text of its own: the data of the cover letter as an editable form to create the LC file from. */
 function coverFormHtml(cover) {
  const fields = cover?.fields || {};
  if (!fields.reference) return '';
  const row = ([key, text]) => `<label>${esc(text)}<input name="${key}" value="${esc(fields[key] || '')}"${key === 'expiryDate' ? ' type="date"' : key === 'amount' ? ' inputmode="decimal"' : ''}></label>`;
  return '<p>Im Dokument steht kein Akkreditivtext (z. B. „siehe beigefügte Kopie"). Die Angaben aus dem <b>Anschreiben</b> wurden erkannt; bitte prüfen, ergänzen und die Akte daraus anlegen. Die Geforderten Dokumente können später nachgetragen werden.</p>' +
   `<form data-cover class="form-grid">${COVER_FIELDS.map(row).join('')}</form>` + (cover.warnings?.length ? `<ul class="avis-messages" role="status">${cover.warnings.map(m => `<li>${esc(m)}</li>`).join('')}</ul>` : '');
 }
 /** The request body for the new LC file from the cover letter form values (empty values become null). */
 function coverRequest(values) {
  const clean = key => (values[key] || '').trim() || null;
  const amount = clean('amount');
  return { reference: (values.reference || '').trim(), ownBankReference: clean('ownBankReference'), foreignBankReference: clean('foreignBankReference'), applicant: clean('applicant'), beneficiary: clean('beneficiary'),
   amount: amount ? Number(amount.replace(',', '.')) : null, currency: clean('currency'), expiryDate: clean('expiryDate'), documentType: 'ADVISING_LETTER', documentDate: null, issuingBank: clean('issuingBank'), expiryPlace: clean('expiryPlace'), copyNumber: null };
 }
 /** Inbox suggestion that means a SWIFT message (MT700 and related). */
 const isSwift = type => /^SWIFT_/.test(String(type || ''));
 globalThis.LcAvisCase = { previewHtml, coverFormHtml, coverRequest, isSwift, annexHtml };
 if (typeof document === 'undefined' || !document.body) return;

 async function open(id, name, swift = false) {
  const dialog = document.createElement('dialog'); dialog.className = 'wide-dialog avis-dialog'; document.body.append(dialog); dialog.onclose = () => dialog.remove();
  dialog.innerHTML = `<div class="dialoghead"><h2>Avis einlesen</h2><button type="button" data-close>Schließen</button></div><p role="status">${esc(name)} wird gelesen …</p>`;
  dialog.querySelector('[data-close]').onclick = () => dialog.close(); dialog.showModal();
  let partners = [], annexId = null;
  try { partners = await json(`/api/inbox/${encodeURIComponent(id)}/avis-partners`); } catch (error) { partners = []; }
  const show = async () => {
   const query = annexId ? `?annex=${encodeURIComponent(annexId)}` : '';
   const result = await json(`/api/inbox/${encodeURIComponent(id)}/avis-preview${query}`);
   // The letter has no credit text of its own but a likely annex is in the inbox: read that one together with it.
   if (!annexId && !result.creditTextFound && partners.length) { annexId = partners[0].id; return show(); }
   const cover = !result.creditTextFound && result.cover?.fields?.reference ? result.cover : null;
   const ok = result.creditTextFound && result.credit?.valid;
   const amendment = result.credit?.messageType === 'MT707';
   const title = swift ? 'SWIFT-Nachricht importieren' : 'Avis einlesen';
   const action = cover ? 'LC-Akte aus Anschreiben anlegen' : amendment ? 'Änderung (MT707) zur Akte übernehmen' : swift ? 'LC-Akte aus SWIFT-Nachricht anlegen' : 'LC-Akte aus Avis anlegen';
   const partyBox = !cover && ok && globalThis.LcPartyReference ? LcPartyReference.html(LcPartyReference.corrections(result.credit)) : '';
   dialog.innerHTML = `<div class="dialoghead"><h2>${title}</h2><button type="button" data-close>Schließen</button></div><p>${esc(name)}${result.credit?.messageType ? ' · ' + esc(result.credit.messageType) : ''}</p>${annexHtml(partners, annexId)}${cover ? coverFormHtml(cover) : previewHtml(result)}${partyBox}` +
    `<p data-error role="alert"></p><div class="dialog-actions"><button type="button" data-create${ok || cover ? '' : ' disabled'}>${action}</button></div>`;
   dialog.querySelector('[data-close]').onclick = () => dialog.close();
   const annexSelect = dialog.querySelector('[data-annex]');
   if (annexSelect) annexSelect.onchange = async () => { annexId = annexSelect.value || null; try { await show(); } catch (error) { dialog.querySelector('[data-error]').textContent = error.message; } };
   dialog.querySelector('[data-create]').onclick = async event => {
    const button = event.currentTarget; button.disabled = true;
    try {
     let created;
     if (cover) {
      const body = coverRequest(Object.fromEntries(new FormData(dialog.querySelector('form[data-cover]'))));
      if (!body.reference) throw new Error('Bitte die Akkreditivnummer angeben.');
      await json(`/api/inbox/${encodeURIComponent(id)}/new-case`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      created = { reference: body.reference };
    } else {
      const box = dialog.querySelector('[data-party-correction]'), party = box ? LcPartyReference.read(box) : null;
      if (party?.missing.length) throw new Error(`Bitte ${party.missing.join(' und ')} ergänzen – die Nachricht verweist nur auf ein anderes Feld.`);
      created = await json(`/api/inbox/${encodeURIComponent(id)}/avis-case${annexId ? '?annex=' + encodeURIComponent(annexId) : ''}`, party ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ applicant: party.applicant, beneficiary: party.beneficiary }) } : { method: 'POST' });
     }
     dialog.close();
     const message = document.querySelector('#inboxMessage');
     if (message) message.textContent = created.kind === 'MT707' ? `Änderung zur LC-Akte „${created.reference}" übernommen (bitte in der Akte annehmen oder ablehnen); die Nachricht ist als Dokument abgelegt.` : `LC-Akte „${created.reference}" angelegt; ${swift ? 'die SWIFT-Nachricht' : 'der Avis'} ist als Dokument abgelegt.`;
     if (typeof load === 'function') await load(); if (typeof loadInbox === 'function') await loadInbox();
    } catch (error) { dialog.querySelector('[data-error]').textContent = error.message; button.disabled = false; }
   };
  };
  try { await show(); } catch (error) { dialog.querySelector('[role=status]').textContent = error.message; }
 }

 function enhance() {
  if (typeof can !== 'function' || !can('LC_EDIT') || !can('DOCUMENT_UPLOAD')) return;
  document.querySelectorAll('.inbox-item:not([data-avis])').forEach(article => {
   if (article.dataset.pending === 'true') return;
   article.dataset.avis = '1';
   const name = article.querySelector('.inbox-item-head b')?.textContent || '';
   // A file the inbox recognised as SWIFT message goes through the SWIFT import directly (also TXT/FIN files); other PDFs can be read as advice.
   const swift = isSwift(article.dataset.suggestedType);
   if ((!swift && !/\.pdf$/i.test(name)) || article.dataset.inboxSource) return;
   const button = document.createElement('button'); button.type = 'button'; button.className = (swift ? '' : 'secondary ') + 'avis-open';
   button.textContent = swift ? 'Als SWIFT-Nachricht importieren …' : 'Als Avis einlesen …';
   button.title = swift ? 'SWIFT-Nachricht (MT700/MT707/MT760) wie im SWIFT-Import einlesen und daraus die LC-Akte anlegen oder die Änderung übernehmen' : 'Akkreditivtext des Avis lesen und daraus eine LC-Akte anlegen';
   button.onclick = () => open(article.dataset.inboxId, name, swift);
   (article.querySelector('.inbox-split-action') || article.querySelector('.inbox-item-head'))?.after(button);
  });
 }
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; enhance(); }, 80); } }).observe(document.body, { childList: true, subtree: true });
 enhance();
})();
