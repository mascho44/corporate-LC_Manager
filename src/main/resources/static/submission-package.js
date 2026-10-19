/* Presenting documents to the bank: a cover letter that lists the documents with originals and copies, and the documents themselves in the usual
   order, numbered like the list, as one download. Open discrepancies of the pre-check are shown before the package is made. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const count = value => { const n = Number(String(value ?? '').trim()); return Number.isInteger(n) && n >= 0 && n <= 99 ? n : null; };
 const text = value => (value == null || String(value).trim() === '') ? null : String(value).trim();

 /** Problem with the chosen rows (message) or null when the package can be made. */
 function problem(rows) {
  const chosen = (rows || []).filter(r => r.included);
  if (!chosen.length) return 'Bitte mindestens ein Dokument wählen.';
  for (const r of chosen) {
   const originals = count(r.originals), copies = count(r.copies);
   if (originals == null || copies == null) return `Originale und Kopien von „${r.filename}“ müssen ganze Zahlen von 0 bis 99 sein.`;
   if (originals + copies === 0) return `Für „${r.filename}“ ist weder ein Original noch eine Kopie angegeben.`;
  }
  return null;
 }
 /** Request body for POST /submission-package: only the chosen rows. */
 function requestBody(form) {
  return { date: form.date, recipient: String(form.recipient || '').trim(), ourReference: text(form.ourReference), remarks: text(form.remarks), letterFormat: form.letterFormat === 'pdf' ? 'pdf' : 'docx',
   documents: (form.rows || []).filter(r => r.included).map(r => ({ documentId: r.documentId, originals: count(r.originals), copies: count(r.copies) })) };
 }
 /** Notice above the form: open discrepancies of the pre-check, or nothing. */
 function warningHtml(plan) {
  const items = (plan.warnings || []).map(w => `<li>${esc(w)}</li>`).join('');
  if (!items) return '';
  const open = Number(plan.openDiscrepancies || 0);
  const head = open > 0 ? `<p class="sp-warning" role="status"><b>Die Vorprüfung zeigt ${open} offene Abweichung${open === 1 ? '' : 'en'}.</b> Bitte vor der Einreichung klären:</p>` : '';
  return `${head}<ul class="sp-notes">${items}</ul>`;
 }
 globalThis.LcSubmissionPackage = { problem, requestBody, warningHtml };
 if (typeof document === 'undefined' || !document.body) return;

 async function open() {
  if (typeof activeLc === 'undefined' || !activeLc) return;
  const lcId = activeLc.id;
  const dialog = document.createElement('dialog'); dialog.className = 'wide-dialog submission-dialog'; document.body.append(dialog); dialog.onclose = () => dialog.remove();
  dialog.innerHTML = '<div class="dialoghead"><h2>Einreichungspaket</h2><button type="button" data-close>Schließen</button></div><p role="status">Wird vorbereitet …</p>';
  dialog.querySelector('[data-close]').onclick = () => dialog.close(); dialog.showModal();
  let plan;
  try { plan = await json(`/api/lcs/${encodeURIComponent(lcId)}/submission-package/plan`); } catch (error) { dialog.querySelector('[role=status]').textContent = error.message; return; }
  const state = { date: plan.date, recipient: plan.recipient || '', ourReference: plan.ourReference || '', remarks: '', letterFormat: 'docx', savePresentationDate: true };
  const rows = plan.documents.map(d => ({ documentId: d.documentId, filename: d.filename, typeLabel: d.typeLabel, documentNumber: d.documentNumber, documentDate: d.documentDate, pages: d.pages, included: d.included, originals: d.originals, copies: d.copies }));
  const render = () => {
   const table = rows.length ? `<table class="sp-table"><thead><tr><th></th><th>Dokument</th><th>Nummer / Datum</th><th>Seiten</th><th>Originale</th><th>Kopien</th></tr></thead><tbody>` +
    rows.map((r, i) => `<tr data-row="${i}"><td><input type="checkbox" data-key="included"${r.included ? ' checked' : ''} aria-label="${esc(r.filename)} einreichen"></td><td><b>${esc(r.typeLabel)}</b><br><small>${esc(r.filename)}</small></td><td>${esc(r.documentNumber || '–')}<br><small>${esc(r.documentDate || '')}</small></td><td>${r.pages ?? '–'}</td>` +
    `<td><input data-key="originals" value="${esc(r.originals)}" inputmode="numeric" size="2"></td><td><input data-key="copies" value="${esc(r.copies)}" inputmode="numeric" size="2"></td></tr>`).join('') + '</tbody></table>' : '';
   dialog.innerHTML = `<div class="dialoghead"><h2>Einreichungspaket</h2><button type="button" data-close>Schließen</button></div>` +
    `<p>Begleitschreiben an die Bank mit der Liste der Dokumente und die Dokumente selbst in der üblichen Reihenfolge, nummeriert wie die Liste, als ZIP.</p>${warningHtml(plan)}` +
    `<div class="sp-head"><label>Datum<input type="date" data-field="date"></label><label>Unsere Referenz<input data-field="ourReference" maxlength="100"></label></div>` +
    `<label>Empfänger (Bank)<textarea data-field="recipient" rows="3" maxlength="1000"></textarea></label><h3>Dokumente</h3>${table || '<p>Keine Dokumente zum Einreichen in der Akte.</p>'}` +
    `<label>Bemerkungen im Schreiben<textarea data-field="remarks" rows="2" maxlength="2000"></textarea></label>` +
    `<label>Format des Begleitschreibens<select data-field="letterFormat"><option value="docx">Word (bearbeitbar)</option><option value="pdf">PDF</option></select></label>` +
    `<label class="sp-check"><input type="checkbox" data-field="savePresentationDate"${state.savePresentationDate ? ' checked' : ''}> Datum als Vorlagedatum in der Akte speichern</label>` +
    `<p data-error role="alert"></p><div class="dialog-actions"><button type="button" data-create${rows.length ? '' : ' disabled'}>Paket herunterladen</button></div>`;
   dialog.querySelector('[data-close]').onclick = () => dialog.close();
   dialog.querySelectorAll('[data-field]').forEach(el => {
    if (el.type === 'checkbox') el.onchange = () => { state[el.dataset.field] = el.checked; };
    else { el.value = state[el.dataset.field] ?? ''; el.oninput = () => { state[el.dataset.field] = el.value; }; }
   });
   dialog.querySelectorAll('tr[data-row]').forEach(tr => tr.querySelectorAll('[data-key]').forEach(el => {
    const row = rows[Number(tr.dataset.row)];
    el[el.type === 'checkbox' ? 'onchange' : 'oninput'] = () => { row[el.dataset.key] = el.type === 'checkbox' ? el.checked : el.value; };
   }));
   dialog.querySelector('[data-create]').onclick = async event => {
    const button = event.currentTarget, error = dialog.querySelector('[data-error]'); error.textContent = '';
    const issue = problem(rows) || (!String(state.recipient).trim() ? 'Bitte den Empfänger (Bank) angeben.' : null);
    if (issue) { error.textContent = issue; return; }
    button.disabled = true;
    try {
     const response = await fetch(`/api/lcs/${encodeURIComponent(lcId)}/submission-package`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': typeof csrfToken === 'string' ? csrfToken : '' }, body: JSON.stringify(requestBody({ ...state, rows })) });
     if (!response.ok) { let message = `HTTP ${response.status}`; try { message = (await response.json()).error || message; } catch (ignored) { /* no JSON body */ } throw new Error(message); }
     const blob = await response.blob();
     const name = /filename="?([^";]+)"?/.exec(response.headers.get('Content-Disposition') || '')?.[1] || 'Einreichung.zip';
     const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = name; document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(link.href), 10000);
     let note = `${name} wurde heruntergeladen.`;
     if (state.savePresentationDate) {
      try { await json(`/api/lcs/${encodeURIComponent(lcId)}/rule-facts/presentation-date`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ date: state.date }) }); note += ' Das Vorlagedatum ist in der Akte gespeichert.'; }
      catch (failure) { note += ` Das Vorlagedatum konnte nicht gespeichert werden: ${failure.message}`; }
     }
     error.textContent = ''; dialog.querySelector('[data-create]').disabled = false;
     const status = document.createElement('p'); status.setAttribute('role', 'status'); status.className = 'sp-done'; status.textContent = note; error.after(status);
    } catch (failure) { error.textContent = failure.message; button.disabled = false; }
   };
  };
  render();
 }

 function enhance() {
  if (typeof can !== 'function' || !can('DOCUMENT_GENERATE')) return;
  const set = document.querySelector('#documentSetBtn') || document.querySelector('#uploadBtn');
  if (!set || document.querySelector('#submissionPackageBtn')) return;
  const button = document.createElement('button'); button.type = 'button'; button.id = 'submissionPackageBtn'; button.className = 'secondary';
  button.textContent = 'Einreichungspaket'; button.title = 'Begleitschreiben und Dokumente für die Bank als ZIP herunterladen';
  button.onclick = open; set.after(button);
 }
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; enhance(); }, 80); } }).observe(document.body, { childList: true, subtree: true });
 enhance();
})();
