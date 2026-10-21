/* Advice from the bank (cover letter + credit text): read the credit text of an inbox PDF, show what was found, and create the LC file from it. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const date = value => value ? new Date(value + 'T00:00:00').toLocaleDateString('de-DE') : '–';
 const money = (amount, currency) => amount == null ? '–' : `${Number(amount).toLocaleString('de-DE', { minimumFractionDigits: 2 })} ${currency || ''}`.trim();
 function previewHtml(result) {
  if (!result.creditTextFound) return '<p role="alert">'+tx('avh.389ddf','Im Dokument wurde kein Akkreditivtext erkannt. Der Avis muss neben dem Anschreiben den Text des Akkreditivs enthalten (Felder wie 20, 31D, 32B, 50, 59). Bitte die Datei prüfen oder die Akte manuell anlegen.')+'</p>';
  const c = result.credit;
  const messages = [...(c.errors || []).map(m => `<li class="avis-error">${esc(m)}</li>`), ...(c.warnings || []).map(m => `<li>${esc(m)}</li>`)].join('');
  const docs = (c.requiredDocuments || []).map(d => `<li>${esc(d)}</li>`).join('');
  return `<dl class="avis-facts"><dt>${tx('avh.18b612','Referenz')}</dt><dd>${esc(c.reference || '–')}</dd><dt>${tx('avh.a5b681','Auftraggeber (Applicant)')}</dt><dd>${esc(c.applicant || '–')}</dd><dt>${tx('avh.391339','Begünstigter')}</dt><dd>${esc(c.beneficiary || '–')}</dd>` +
   `<dt>${tx('avh.000374','Betrag')}</dt><dd>${esc(money(c.amount, c.currency))}</dd><dt>${tx('avh.708dee','Ablauf')}</dt><dd>${esc(date(c.expiryDate))}${c.expiryPlace ? ' · ' + esc(c.expiryPlace) : ''}</dd></dl>` +
   (docs ? `<h3>${tx('avh.06c5c3','Geforderte Dokumente (')}${(c.requiredDocuments || []).length})</h3><ul class="avis-docs">${docs}</ul>` : '<p>'+tx('avh.58f8b5','Keine Dokumentenanforderungen erkannt.')+'</p>') +
   (messages ? `<ul class="avis-messages" role="status">${messages}</ul>` : '') +
   `<p class="avis-note"><small>${tx('avh.35fb58','Die Angaben stammen aus dem Text des Avis und sind unbestätigt. Bitte mit dem Original vergleichen; beim Anlegen werden alle Felder des Akkreditivtextes übernommen.')}</small></p>`;
 }
 /** Choice of the annex file (credit text) that belongs to the letter; empty when the inbox holds no other file with a credit text. */
 function annexHtml(partners, selected) {
  if (!partners?.length) return '';
  const options = partners.map(p => `<option value="${esc(p.id)}"${p.id === selected ? ' selected' : ''}>${esc(p.filename)} · ${esc(p.reason)}</option>`).join('');
  return `<label class="avis-annex">${tx('avh.f94029','Anlage mit dem Akkreditivtext')}<select data-annex><option value="">${tx('avh.b1aeeb','keine – nur dieses Dokument')}</option>${options}</select></label>` +
   (selected ? '<p class="avis-note"><small>'+tx('avh.3078ee','Der Akkreditivtext wird aus der Anlage gelesen. Das Schreiben und die Anlage werden beide in der neuen Akte abgelegt.')+'</small></p>' : '');
 }
 const COVER_FIELDS = (globalThis.lcLazy || (f => f()))(() => ([['reference', tx('avl.ee4ea0','Akkreditivnummer *')], ['applicant', tx('avl.a5b681','Auftraggeber (Applicant)')], ['beneficiary', tx('avl.391339','Begünstigter')], ['amount', tx('avl.000374','Betrag')], ['currency', tx('avl.f73cd5','Währung')], ['expiryDate', tx('avl.abf7ac','Gültig bis')], ['expiryPlace', tx('avl.473390','Ort')], ['issuingBank', tx('avl.56781e','Eröffnende Bank')], ['ownBankReference', tx('avl.cf5c95','Referenz eigene Bank')], ['foreignBankReference', tx('avl.5423bb','Referenz Fremdbank')]]));
 /** Advice without credit text of its own: the data of the cover letter as an editable form to create the LC file from. */
 function coverFormHtml(cover) {
  const fields = cover?.fields || {};
  if (!fields.reference) return '';
  const row = ([key, text]) => `<label>${esc(text)}<input name="${key}" value="${esc(fields[key] || '')}"${key === 'expiryDate' ? ' type="date"' : key === 'amount' ? ' inputmode="decimal"' : ''}></label>`;
  return '<p>'+tx('avh.159d5d','Im Dokument steht kein Akkreditivtext (z. B. „siehe beigefügte Kopie"). Die Angaben aus dem')+' <b>'+tx('avh.89124d','Anschreiben')+'</b> '+tx('avh.6a0ee6','wurden erkannt; bitte prüfen, ergänzen und die Akte daraus anlegen. Die Geforderten Dokumente können später nachgetragen werden.')+'</p>' +
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
  dialog.innerHTML = `<div class="dialoghead"><h2>${tx('avh.bc76a7','Avis einlesen')}</h2><button type="button" data-close>${tx('avh.8311b9','Schließen')}</button></div><p role="status">${tx('av.reading','{name} wird gelesen …',{name:esc(name)})}</p>`;
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
   const title = swift ? tx('avl.aff476','SWIFT-Nachricht importieren') : tx('avl.bc76a7','Avis einlesen');
   const action = cover ? tx('avl.ae962f','LC-Akte aus Anschreiben anlegen') : amendment ? tx('avl.fa6fe9','Änderung (MT707) zur Akte übernehmen') : swift ? tx('avl.abf335','LC-Akte aus SWIFT-Nachricht anlegen') : tx('avl.82db0f','LC-Akte aus Avis anlegen');
   const partyBox = !cover && ok && globalThis.LcPartyReference ? LcPartyReference.html(LcPartyReference.corrections(result.credit)) : '';
   dialog.innerHTML = `<div class="dialoghead"><h2>${title}</h2><button type="button" data-close>${tx('avh.8311b9','Schließen')}</button></div><p>${esc(name)}${result.credit?.messageType ? ' · ' + esc(result.credit.messageType) : ''}</p>${annexHtml(partners, annexId)}${cover ? coverFormHtml(cover) : previewHtml(result)}${partyBox}` +
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
      if (!body.reference) throw new Error(tx('avl.de567d','Bitte die Akkreditivnummer angeben.'));
      await json(`/api/inbox/${encodeURIComponent(id)}/new-case`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      created = { reference: body.reference };
    } else {
      const box = dialog.querySelector('[data-party-correction]'), party = box ? LcPartyReference.read(box) : null;
      if (party?.missing.length) throw new Error(tx('av.missingParty','Bitte {list} ergänzen – die Nachricht verweist nur auf ein anderes Feld.',{list:party.missing.join(tx('av.and',' und '))}));
      created = await json(`/api/inbox/${encodeURIComponent(id)}/avis-case${annexId ? '?annex=' + encodeURIComponent(annexId) : ''}`, party ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ applicant: party.applicant, beneficiary: party.beneficiary }) } : { method: 'POST' });
     }
     dialog.close();
     const message = document.querySelector('#inboxMessage');
     if (message) message.textContent = created.kind === 'MT707' ? tx('avt.a700cc','Änderung zur LC-Akte „{p0}" übernommen (bitte in der Akte annehmen oder ablehnen); die Nachricht ist als Dokument abgelegt.',{p0:created.reference}) : `LC-Akte „${created.reference}" angelegt; ${swift ? 'die SWIFT-Nachricht' : 'der Avis'} ist als Dokument abgelegt.`;
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
   button.textContent = swift ? tx('avl.070548','Als SWIFT-Nachricht importieren …') : tx('avl.131f41','Als Avis einlesen …');
   button.title = swift ? tx('avl.32a724','SWIFT-Nachricht (MT700/MT707/MT760) wie im SWIFT-Import einlesen und daraus die LC-Akte anlegen oder die Änderung übernehmen') : tx('avl.7c9b4f','Akkreditivtext des Avis lesen und daraus eine LC-Akte anlegen');
   button.onclick = () => open(article.dataset.inboxId, name, swift);
   (article.querySelector('.inbox-split-action') || article.querySelector('.inbox-item-head'))?.after(button);
  });
 }
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; enhance(); }, 80); } }).observe(document.body, { childList: true, subtree: true });
 enhance();
})();
