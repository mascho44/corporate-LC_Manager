/* All documents of a shipment in one step: one form (invoice number, date, goods lines, packing data) creates invoice, packing list and the
   certificates the credit names, then shows the notes and the findings of the automatic check. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const num = value => { const n = Number(String(value ?? '').trim().replace(',', '.')); return String(value ?? '').trim() === '' || !Number.isFinite(n) ? null : n; };
 const money = (value, currency) => value == null ? '–' : `${Number(value).toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}${currency ? ' ' + currency : ''}`;
 const SEVERITY = { DISCREPANCY: 'Abweichung', WARNING: 'Hinweis', OK: 'in Ordnung' };

 /** Amount of one goods line: the entered amount, otherwise quantity times unit price (two decimals). */
 function lineAmount(line) {
  const amount = num(line.amount);
  if (amount != null) return amount;
  const quantity = num(line.quantity), price = num(line.unitPrice);
  return quantity != null && price != null ? Math.round(quantity * price * 100) / 100 : null;
 }
 /** Sum of all lines, or null while a line has no amount. */
 function totalOf(lines) {
  let sum = 0;
  for (const line of lines || []) { const amount = lineAmount(line); if (amount == null) return null; sum += amount; }
  return Math.round(sum * 100) / 100;
 }
 /** Message under the totals: over the highest allowed invoice total, or how much room is left. */
 function limitMessage(total, maximum, currency) {
  if (total == null) return { level: 'hint', text: 'Summe: Beträge der Positionen fehlen noch.' };
  if (maximum == null) return { level: 'ok', text: `Summe: ${money(total, currency)}` };
  if (total > maximum + 0.0001) return { level: 'error', text: `Summe ${money(total, currency)} liegt ${money(total - maximum, currency)} über dem zulässigen Höchstbetrag ${money(maximum, currency)}.` };
  return { level: 'ok', text: `Summe ${money(total, currency)} · zulässig bis ${money(maximum, currency)}` };
 }
 /** The request for POST /document-set from the form values. */
 function requestBody(form) {
  const text = v => (v == null || String(v).trim() === '') ? null : String(v).trim();
  return {
   documentDate: form.documentDate, invoiceNumber: String(form.invoiceNumber || '').trim(), notes: text(form.notes), types: form.types, format: form.format === 'pdf' ? 'pdf' : 'docx',
   quantity: null, packages: form.packages == null || form.packages === '' ? null : Math.round(num(form.packages)),
   netWeight: num(form.netWeight), grossWeight: num(form.grossWeight),
   items: (form.lines || []).filter(l => text(l.description) || num(l.quantity) != null).map((l, i) => ({
    position: String(i + 1), description: text(l.description), quantity: num(l.quantity), unit: text(l.unit), unitPrice: num(l.unitPrice), amount: num(l.amount),
    packages: l.packages == null || l.packages === '' ? null : Math.round(num(l.packages)), netWeight: num(l.netWeight), grossWeight: num(l.grossWeight) }))
  };
 }
 /** The result: created documents with download link, notes and findings. */
 function resultHtml(result) {
  const docs = (result.documents || []).map(d => `<li><a href="${esc(d.downloadUrl)}">${esc(d.originalFilename)}</a> <small>${esc(d.typeLabel)}</small></li>`).join('');
  const warnings = (result.warnings || []).map(w => `<li class="ds-warning">${esc(w)}</li>`).join('');
  const findings = (result.findings || []).map(f => `<li class="ds-${esc(String(f.severity).toLowerCase())}"><b>${esc(SEVERITY[f.severity] || f.severity)}</b> ${esc(f.message)} <small>${esc(f.documentName)}</small></li>`).join('');
  return `<h3>${(result.documents || []).length} Dokumente erzeugt</h3><ul class="ds-docs">${docs}</ul>` +
   (result.total != null ? `<p>Rechnungssumme: <b>${esc(money(result.total, result.currency))}</b></p>` : '') +
   (warnings ? `<h3>Hinweise</h3><ul class="ds-notes">${warnings}</ul>` : '') +
   (findings ? `<h3>Ergebnis der automatischen Prüfung</h3><ul class="ds-notes">${findings}</ul>` : '<p class="ds-ok">Die automatische Prüfung findet an den neuen Dokumenten nichts Auffälliges.</p>');
 }
 globalThis.LcDocumentSet = { lineAmount, totalOf, limitMessage, requestBody, resultHtml };
 if (typeof document === 'undefined' || !document.body) return;

 const LINE_FIELDS = [['description', 'Beschreibung', 'wide'], ['quantity', 'Menge'], ['unit', 'Einheit'], ['unitPrice', 'Einzelpreis'], ['amount', 'Betrag'], ['packages', 'Packstücke'], ['netWeight', 'Netto kg'], ['grossWeight', 'Brutto kg']];

 async function open() {
  if (typeof activeLc === 'undefined' || !activeLc) return;
  const lcId = activeLc.id;
  const dialog = document.createElement('dialog'); dialog.className = 'wide-dialog document-set-dialog'; document.body.append(dialog); dialog.onclose = () => dialog.remove();
  dialog.innerHTML = '<div class="dialoghead"><h2>Dokumentensatz erzeugen</h2><button type="button" data-close>Schließen</button></div><p role="status">Wird vorbereitet …</p>';
  dialog.querySelector('[data-close]').onclick = () => dialog.close(); dialog.showModal();
  let proposal;
  try { proposal = await json(`/api/lcs/${encodeURIComponent(lcId)}/document-set/proposal`); } catch (error) { dialog.querySelector('[role=status]').textContent = error.message; return; }
  const currency = proposal.currency || '';
  const lines = proposal.items.map(i => ({ description: i.description || '', quantity: i.quantity ?? '', unit: i.unit || '', unitPrice: i.unitPrice ?? '', amount: i.amount ?? '', packages: '', netWeight: '', grossWeight: '' }));
  const chosen = new Set(proposal.types);
  const render = () => {
   const rows = lines.map((line, i) => `<div class="ds-line" data-line="${i}">${LINE_FIELDS.map(([key, label, wide]) => `<label class="${wide || ''}">${esc(label)}${key === 'description' ? `<textarea data-key="${key}" rows="2">${esc(line[key])}</textarea>` : `<input data-key="${key}" value="${esc(line[key])}" inputmode="decimal">`}</label>`).join('')}${lines.length > 1 ? '<button type="button" class="secondary" data-remove>Entfernen</button>' : ''}</div>`).join('');
   const boxes = proposal.requirements.map(r => `<label class="ds-type"><input type="checkbox" data-type="${esc(r.documentType)}"${chosen.has(r.documentType) ? ' checked' : ''}> ${esc(r.label)}${r.namedInCredit ? ' <small>(im Akkreditiv verlangt)</small>' : ''}${r.originals ? ` <small>· ${esc(r.originals)} Originale</small>` : ''}${r.signed ? ' <small>· unterschrieben</small>' : ''}</label>`).join('');
   const total = limitMessage(totalOf(lines), proposal.maximumInvoiceTotal, currency);
   dialog.innerHTML = `<div class="dialoghead"><h2>Dokumentensatz erzeugen</h2><button type="button" data-close>Schließen</button></div>` +
    `<p>Aus einer Eingabe entstehen alle gewählten Dokumente mit gleicher Nummer, gleichem Datum und gleichen Positionen. Danach werden sie gegen das Akkreditiv geprüft.</p>` +
    (proposal.companyConfigured === false ? '<p class="ds-hint" role="status">Für diese Akte ist keine Firma hinterlegt. Der Briefkopf zeigt nur den Begünstigten aus dem Akkreditiv. Anschrift, Logo und Bankverbindung kommen aus dem Firmenprofil (Mandanteneinstellungen → Firma).</p>' : '') +
    `<div class="ds-head"><label>Rechnungsnummer<input data-field="invoiceNumber" value="${esc(proposal.invoiceNumber)}"></label><label>Dokumentdatum<input type="date" data-field="documentDate" value="${esc(proposal.documentDate)}"></label></div>` +
    `<h3>Dokumente</h3><div class="ds-types">${boxes}</div><h3>Positionen</h3>${rows}<button type="button" class="secondary" data-add>+ Position</button><p class="ds-total ds-${total.level}" role="status">${esc(total.text)}</p>` +
    `<label>Hinweise / Markierungen (Marks and numbers)<textarea data-field="notes" rows="2" maxlength="4000"></textarea></label><label>Format<select data-field="format"><option value="docx">Word (bearbeitbar)</option><option value="pdf">PDF (fertig zum Versand)</option></select></label><p data-error role="alert"></p><div class="dialog-actions"><button type="button" data-create>Dokumente erzeugen</button></div>`;
   dialog.querySelector('[data-close]').onclick = () => dialog.close();
   dialog.querySelector('[data-field=invoiceNumber]').value = state.invoiceNumber; dialog.querySelector('[data-field=documentDate]').value = state.documentDate; dialog.querySelector('[data-field=notes]').value = state.notes; dialog.querySelector('[data-field=format]').value = state.format;
   dialog.querySelectorAll('[data-field]').forEach(el => el.oninput = () => { state[el.dataset.field] = el.value; });
   dialog.querySelectorAll('[data-type]').forEach(el => el.onchange = () => { if (el.checked) chosen.add(el.dataset.type); else chosen.delete(el.dataset.type); });
   dialog.querySelectorAll('.ds-line').forEach(box => {
    const i = Number(box.dataset.line);
    box.querySelectorAll('[data-key]').forEach(el => el.oninput = () => { lines[i][el.dataset.key] = el.value; const m = limitMessage(totalOf(lines), proposal.maximumInvoiceTotal, currency), p = dialog.querySelector('.ds-total'); p.textContent = m.text; p.className = `ds-total ds-${m.level}`; });
    const remove = box.querySelector('[data-remove]'); if (remove) remove.onclick = () => { lines.splice(i, 1); render(); };
   });
   dialog.querySelector('[data-add]').onclick = () => { lines.push({ description: '', quantity: '', unit: '', unitPrice: '', amount: '', packages: '', netWeight: '', grossWeight: '' }); render(); };
   dialog.querySelector('[data-create]').onclick = async event => {
    const button = event.currentTarget; button.disabled = true;
    try {
     const body = requestBody({ ...state, lines, types: [...chosen] });
     if (!body.items.length) throw new Error('Bitte mindestens eine Position angeben.');
     if (!body.types.length) throw new Error('Bitte mindestens ein Dokument wählen.');
     const result = await json(`/api/lcs/${encodeURIComponent(lcId)}/document-set`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
     dialog.innerHTML = `<div class="dialoghead"><h2>Dokumentensatz erzeugt</h2><button type="button" data-close>Schließen</button></div>${resultHtml(result)}`;
     dialog.querySelector('[data-close]').onclick = async () => { dialog.close(); if (typeof show === 'function') { try { await show(lcId); } catch (ignored) { /* the list refreshes on the next visit */ } } };
    } catch (error) { dialog.querySelector('[data-error]').textContent = error.message; button.disabled = false; }
   };
  };
  const state = { format: 'docx', invoiceNumber: proposal.invoiceNumber, documentDate: proposal.documentDate, notes: '', packages: '', netWeight: '', grossWeight: '' };
  render();
 }

 function enhance() {
  if (typeof can !== 'function' || !can('DOCUMENT_GENERATE')) return;
  const upload = document.querySelector('#uploadBtn');
  if (!upload || document.querySelector('#documentSetBtn')) return;
  const button = document.createElement('button'); button.type = 'button'; button.id = 'documentSetBtn'; button.className = 'secondary';
  button.textContent = 'Dokumentensatz erzeugen'; button.title = 'Rechnung, Packliste und Zertifikate aus einer Eingabe erzeugen und gegen das Akkreditiv prüfen';
  button.onclick = open; upload.before(button);
 }
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; enhance(); }, 80); } }).observe(document.body, { childList: true, subtree: true });
 enhance();
})();
