/* "Entwurf prüfen": a draft from the forwarder, insurer or chamber is read and checked against the credit before the original is issued.
   The server keeps nothing; the result is shown here only. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? `{${n}}`)); };
 const TYPES = [['BILL_OF_LADING', 'Konnossement (Bill of Lading)'], ['AIR_WAYBILL', 'Luftfrachtbrief (AWB)'], ['ROAD_CONSIGNMENT_NOTE', 'CMR-Frachtbrief'],
  ['SEA_WAYBILL', 'Seefrachtbrief'], ['MULTIMODAL_TRANSPORT_DOCUMENT', 'Multimodales Transportdokument'], ['INSURANCE_CERTIFICATE', 'Versicherungszertifikat'],
  ['CERTIFICATE_OF_ORIGIN', 'Ursprungszeugnis'], ['INSPECTION_CERTIFICATE', 'Inspektionszertifikat'], ['QUALITY_CERTIFICATE', 'Qualitätszertifikat'],
  ['COMMERCIAL_INVOICE', 'Handelsrechnung'], ['PACKING_LIST', 'Packliste'], ['BILL_OF_EXCHANGE', 'Tratte']];
 const typeLabel = ([value, de]) => tr('dc.type.' + value, de);

 /** The verdict on the draft: findings with what to change, discrepancies first. */
 function resultHtml(result) {
  const date = result.extractedDate ? new Date(result.extractedDate + 'T00:00:00').toLocaleDateString('de-DE') : '–';
  const read = `<p class="dc-read">${tr('dc.read', 'Gelesen: Referenz {ref} · Datum {date}', { ref: esc(result.extractedReference || '–'), date: esc(date) })}${result.extractedAmount != null ? ` · ${tr('dc.amount', 'Betrag')} ${esc(result.extractedAmount)} ${esc(result.extractedCurrency || '')}` : ''}</p>`;
  if (!result.textFound) return `<p class="dc-bad">${tr('dc.noText', 'Im Entwurf wurde kein Text gefunden. Bitte eine Text-PDF anfordern oder einen lesbaren Scan hochladen; ohne Text ist keine Prüfung möglich.')}</p>`;
  const head = result.discrepancies
   ? `<p class="dc-bad"><b>✗ ${result.discrepancies === 1 ? tr('dc.oneDiscrepancy', '1 Abweichung') : tr('dc.discrepancies', '{n} Abweichungen', { n: result.discrepancies })}</b> – ${tr('dc.fixBefore', 'vor der Ausstellung des Originals korrigieren lassen.')}</p>`
   : `<p class="dc-ok"><b>✓ ${tr('dc.clean', 'Keine Abweichung gefunden')}</b>${result.warnings ? ' · ' + (result.warnings === 1 ? tr('dc.checkWarning', '1 Hinweis bitte prüfen') : tr('dc.checkWarnings', '{n} Hinweise bitte prüfen', { n: result.warnings })) : ''}.</p>`;
  const items = (result.findings || []).map(f => `<li class="dc-${esc(String(f.severity).toLowerCase())}"><b>${f.severity === 'DISCREPANCY' ? tr('dc.discrepancy', 'Abweichung') : tr('dc.warning', 'Hinweis')}:</b> ${esc(f.message)}` +
   (f.advice ? `<br><small>${tr('corp.fix', 'So beheben')}: ${esc(f.advice)}</small>` : '') + (f.lcCondition ? `<br><small>${tr('dc.credit', 'Akkreditiv')}: ${esc(f.lcCondition)}</small>` : '') + '</li>').join('');
  return head + read + (items ? `<ul class="dc-findings">${items}</ul>` : '') +
   `<p class="dc-note"><small>${tr('dc.note', 'Automatische Vorprüfung des Entwurfs gegen das Akkreditiv und die vorhandenen Dokumente. Der Entwurf wurde nicht gespeichert.')}</small></p>`;
 }
 globalThis.LcDraftCheck = { resultHtml, TYPES };
 if (typeof document === 'undefined' || !document.body) return;

 function open() {
  if (typeof activeLc === 'undefined' || !activeLc) return;
  const lcId = activeLc.id;
  const dialog = document.createElement('dialog'); dialog.className = 'wide-dialog draft-check-dialog'; document.body.append(dialog); dialog.onclose = () => dialog.remove();
  dialog.innerHTML = `<div class="dialoghead"><h2>${tr('dc.title', 'Entwurf prüfen')}</h2><button type="button" data-close>${tr('common.close', 'Schließen')}</button></div>` +
   `<p>${tr('dc.intro', 'Entwurf eines Dritten (zum Beispiel Konnossement vom Spediteur) vor der Ausstellung gegen das Akkreditiv prüfen. Der Entwurf wird nicht in der Akte gespeichert.')}</p>` +
   `<label>${tr('dc.typeLabel', 'Dokumentart')}<select data-type>${TYPES.map(entry => `<option value="${entry[0]}">${esc(typeLabel(entry))}</option>`).join('')}</select></label>` +
   `<label>${tr('dc.file', 'Datei (PDF oder Bild)')}<input type="file" data-file accept=".pdf,image/*"></label><p data-error role="alert"></p>` +
   `<div class="dialog-actions"><button type="button" data-run>${tr('dc.run', 'Prüfen')}</button></div><div data-result aria-live="polite"></div>`;
  dialog.querySelector('[data-close]').onclick = () => dialog.close();
  dialog.querySelector('[data-run]').onclick = async event => {
   const button = event.currentTarget, error = dialog.querySelector('[data-error]'), out = dialog.querySelector('[data-result]'), file = dialog.querySelector('[data-file]').files[0];
   error.textContent = ''; if (!file) { error.textContent = tr('dc.chooseFile', 'Bitte eine Datei wählen.'); return; }
   button.disabled = true; out.textContent = tr('dc.running', 'Der Entwurf wird gelesen und geprüft …');
   try {
    const form = new FormData(); form.append('file', file);
    const response = await fetch(`/api/lcs/${encodeURIComponent(lcId)}/draft-check?type=${encodeURIComponent(dialog.querySelector('[data-type]').value)}`,
     { method: 'POST', headers: { 'X-CSRF-TOKEN': typeof csrfToken === 'string' ? csrfToken : '' }, body: form });
    if (!response.ok) { let message = `HTTP ${response.status}`; try { message = (await response.json()).error || message; } catch (ignored) { /* no JSON body */ } throw new Error(message); }
    out.innerHTML = resultHtml(await response.json());
   } catch (e) { out.textContent = ''; error.textContent = tr('dc.failed', 'Prüfung fehlgeschlagen') + ': ' + e.message; }
   finally { button.disabled = false; }
  };
  dialog.showModal();
 }

 function enhance() {
  if (typeof can !== 'function' || !can('DOCUMENT_UPLOAD')) return;
  const upload = document.querySelector('#uploadBtn');
  if (!upload || document.querySelector('#draftCheckBtn')) return;
  const button = document.createElement('button'); button.type = 'button'; button.id = 'draftCheckBtn'; button.className = 'secondary';
  button.textContent = tr('dc.title', 'Entwurf prüfen'); button.title = tr('dc.buttonTitle', 'Entwurf eines Dritten vor der Ausstellung gegen das Akkreditiv prüfen, ohne ihn zu speichern');
  button.onclick = open; upload.before(button);
 }
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; enhance(); }, 80); } }).observe(document.body, { childList: true, subtree: true });
 enhance();
})();
