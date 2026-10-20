/* "Entwurf prüfen": a draft from the forwarder, insurer or chamber is read and checked against the credit before the original is issued.
   The server keeps nothing; the result is shown here only. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const TYPES = [['BILL_OF_LADING', 'Konnossement (Bill of Lading)'], ['AIR_WAYBILL', 'Luftfrachtbrief (AWB)'], ['ROAD_CONSIGNMENT_NOTE', 'CMR-Frachtbrief'],
  ['SEA_WAYBILL', 'Seefrachtbrief'], ['MULTIMODAL_TRANSPORT_DOCUMENT', 'Multimodales Transportdokument'], ['INSURANCE_CERTIFICATE', 'Versicherungszertifikat'],
  ['CERTIFICATE_OF_ORIGIN', 'Ursprungszeugnis'], ['INSPECTION_CERTIFICATE', 'Inspektionszertifikat'], ['QUALITY_CERTIFICATE', 'Qualitätszertifikat'],
  ['COMMERCIAL_INVOICE', 'Handelsrechnung'], ['PACKING_LIST', 'Packliste'], ['BILL_OF_EXCHANGE', 'Tratte']];

 /** The verdict on the draft: findings with what to change, discrepancies first. */
 function resultHtml(result) {
  const date = result.extractedDate ? new Date(result.extractedDate + 'T00:00:00').toLocaleDateString('de-DE') : '–';
  const read = `<p class="dc-read">Gelesen: Referenz ${esc(result.extractedReference || '–')} · Datum ${esc(date)}${result.extractedAmount != null ? ` · Betrag ${esc(result.extractedAmount)} ${esc(result.extractedCurrency || '')}` : ''}</p>`;
  if (!result.textFound) return '<p class="dc-bad">Im Entwurf wurde kein Text gefunden. Bitte eine Text-PDF anfordern oder einen lesbaren Scan hochladen; ohne Text ist keine Prüfung möglich.</p>';
  const head = result.discrepancies
   ? `<p class="dc-bad"><b>✗ ${result.discrepancies} Abweichung${result.discrepancies === 1 ? '' : 'en'}</b> – vor der Ausstellung des Originals korrigieren lassen.</p>`
   : `<p class="dc-ok"><b>✓ Keine Abweichung gefunden</b>${result.warnings ? ` · ${result.warnings} Hinweis${result.warnings === 1 ? '' : 'e'} bitte prüfen` : ''}.</p>`;
  const items = (result.findings || []).map(f => `<li class="dc-${esc(String(f.severity).toLowerCase())}"><b>${f.severity === 'DISCREPANCY' ? 'Abweichung' : 'Hinweis'}:</b> ${esc(f.message)}` +
   (f.advice ? `<br><small>So beheben: ${esc(f.advice)}</small>` : '') + (f.lcCondition ? `<br><small>Akkreditiv: ${esc(f.lcCondition)}</small>` : '') + '</li>').join('');
  return head + read + (items ? `<ul class="dc-findings">${items}</ul>` : '') +
   '<p class="dc-note"><small>Automatische Vorprüfung des Entwurfs gegen das Akkreditiv und die vorhandenen Dokumente. Der Entwurf wurde nicht gespeichert.</small></p>';
 }
 globalThis.LcDraftCheck = { resultHtml, TYPES };
 if (typeof document === 'undefined' || !document.body) return;

 function open() {
  if (typeof activeLc === 'undefined' || !activeLc) return;
  const lcId = activeLc.id;
  const dialog = document.createElement('dialog'); dialog.className = 'wide-dialog draft-check-dialog'; document.body.append(dialog); dialog.onclose = () => dialog.remove();
  dialog.innerHTML = `<div class="dialoghead"><h2>Entwurf prüfen</h2><button type="button" data-close>Schließen</button></div>` +
   `<p>Entwurf eines Dritten (zum Beispiel Konnossement vom Spediteur) vor der Ausstellung gegen das Akkreditiv prüfen. Der Entwurf wird nicht in der Akte gespeichert.</p>` +
   `<label>Dokumentart<select data-type>${TYPES.map(([value, label]) => `<option value="${value}">${esc(label)}</option>`).join('')}</select></label>` +
   `<label>Datei (PDF oder Bild)<input type="file" data-file accept=".pdf,image/*"></label><p data-error role="alert"></p>` +
   `<div class="dialog-actions"><button type="button" data-run>Prüfen</button></div><div data-result aria-live="polite"></div>`;
  dialog.querySelector('[data-close]').onclick = () => dialog.close();
  dialog.querySelector('[data-run]').onclick = async event => {
   const button = event.currentTarget, error = dialog.querySelector('[data-error]'), out = dialog.querySelector('[data-result]'), file = dialog.querySelector('[data-file]').files[0];
   error.textContent = ''; if (!file) { error.textContent = 'Bitte eine Datei wählen.'; return; }
   button.disabled = true; out.textContent = 'Der Entwurf wird gelesen und geprüft …';
   try {
    const form = new FormData(); form.append('file', file);
    const response = await fetch(`/api/lcs/${encodeURIComponent(lcId)}/draft-check?type=${encodeURIComponent(dialog.querySelector('[data-type]').value)}`,
     { method: 'POST', headers: { 'X-CSRF-TOKEN': typeof csrfToken === 'string' ? csrfToken : '' }, body: form });
    if (!response.ok) { let message = `HTTP ${response.status}`; try { message = (await response.json()).error || message; } catch (ignored) { /* no JSON body */ } throw new Error(message); }
    out.innerHTML = resultHtml(await response.json());
   } catch (e) { out.textContent = ''; error.textContent = 'Prüfung fehlgeschlagen: ' + e.message; }
   finally { button.disabled = false; }
  };
  dialog.showModal();
 }

 function enhance() {
  if (typeof can !== 'function' || !can('DOCUMENT_UPLOAD')) return;
  const upload = document.querySelector('#uploadBtn');
  if (!upload || document.querySelector('#draftCheckBtn')) return;
  const button = document.createElement('button'); button.type = 'button'; button.id = 'draftCheckBtn'; button.className = 'secondary';
  button.textContent = 'Entwurf prüfen'; button.title = 'Entwurf eines Dritten vor der Ausstellung gegen das Akkreditiv prüfen, ohne ihn zu speichern';
  button.onclick = open; upload.before(button);
 }
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; enhance(); }, 80); } }).observe(document.body, { childList: true, subtree: true });
 enhance();
})();
