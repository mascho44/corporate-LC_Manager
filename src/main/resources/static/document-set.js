/* All documents of a shipment in one step: one form (invoice number, date, goods lines, packing data) creates invoice, packing list and the
   certificates the credit names, then shows the notes and the findings of the automatic check. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? `{${n}}`)); };
 const num = value => { const n = Number(String(value ?? '').trim().replace(',', '.')); return String(value ?? '').trim() === '' || !Number.isFinite(n) ? null : n; };
 const money = (value, currency) => value == null ? '–' : `${Number(value).toLocaleString(globalThis.LcI18n?.language?.() === 'en' ? 'en-GB' : 'de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}${currency ? ' ' + currency : ''}`;
 const severity = s => ({ DISCREPANCY: tr('dc.discrepancy', 'Abweichung'), WARNING: tr('dc.warning', 'Hinweis'), OK: tr('ds.ok', 'in Ordnung') }[s] || s);

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
  if (total == null) return { level: 'hint', text: tr('ds.sumMissing', 'Summe: Beträge der Positionen fehlen noch.') };
  if (maximum == null) return { level: 'ok', text: tr('ds.sum', 'Summe: {v}', { v: money(total, currency) }) };
  if (total > maximum + 0.0001) return { level: 'error', text: tr('ds.sumOver', 'Summe {v} liegt {over} über dem zulässigen Höchstbetrag {max}.', { v: money(total, currency), over: money(total - maximum, currency), max: money(maximum, currency) }) };
  return { level: 'ok', text: tr('ds.sumOk', 'Summe {v} · zulässig bis {max}', { v: money(total, currency), max: money(maximum, currency) }) };
 }
 /** The request for POST /document-set from the form values. */
 function requestBody(form) {
  const text = v => (v == null || String(v).trim() === '') ? null : String(v).trim();
  return {
   documentDate: form.documentDate, invoiceNumber: String(form.invoiceNumber || '').trim(), notes: text(form.notes), types: form.types, format: form.format === 'pdf' ? 'pdf' : 'docx', replaceSetId: form.replaceSetId || null,
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
  const findings = (result.findings || []).map(f => `<li class="ds-${esc(String(f.severity).toLowerCase())}"><b>${esc(severity(f.severity))}</b> ${esc(f.message)} <small>${esc(f.documentName)}</small></li>`).join('');
  return `<h3>${tr('ds.created', '{n} Dokumente erzeugt', { n: (result.documents || []).length })}</h3><ul class="ds-docs">${docs}</ul>` +
   (result.total != null ? `<p>${tr('ds.invoiceTotal', 'Rechnungssumme')}: <b>${esc(money(result.total, result.currency))}</b></p>` : '') +
   (warnings ? `<h3>${tr('ds.notes', 'Hinweise')}</h3><ul class="ds-notes">${warnings}</ul>` : '') +
   (findings ? `<h3>${tr('ds.checkResult', 'Ergebnis der automatischen Prüfung')}</h3><ul class="ds-notes">${findings}</ul>` : `<p class="ds-ok">${tr('ds.checkClean', 'Die automatische Prüfung findet an den neuen Dokumenten nichts Auffälliges.')}</p>`);
 }
 /** Form state from the stored input of an earlier set, so a corrected set starts from what was entered then. */
 function fromPrevious(request) {
  const value = v => v == null ? '' : String(v);
  return {
   state: { format: request.format === 'pdf' ? 'pdf' : 'docx', invoiceNumber: value(request.invoiceNumber), documentDate: value(request.documentDate), notes: value(request.notes),
    packages: value(request.packages), netWeight: value(request.netWeight), grossWeight: value(request.grossWeight) },
   lines: (request.items || []).map(i => ({ description: value(i.description), quantity: value(i.quantity), unit: value(i.unit), unitPrice: value(i.unitPrice), amount: value(i.amount),
    packages: value(i.packages), netWeight: value(i.netWeight), grossWeight: value(i.grossWeight) })),
   types: [...(request.types || [])]
  };
 }
 function previousLabel(set) {
  const when = set.createdAt ? new Date(set.createdAt).toLocaleString(globalThis.LcI18n?.language?.() === 'en' ? 'en-GB' : 'de-DE', { dateStyle: 'short', timeStyle: 'short' }) : '';
  return tr(set.documents.length === 1 ? 'ds.previousOne' : 'ds.previousMany', set.documents.length === 1 ? 'Satz vom {when} · Rechnung {inv} · 1 Dokument' : 'Satz vom {when} · Rechnung {inv} · {n} Dokumente', { when, inv: set.request?.invoiceNumber || '–', n: set.documents.length });
 }
 globalThis.LcDocumentSet = { lineAmount, totalOf, limitMessage, requestBody, resultHtml, fromPrevious, previousLabel };
 if (typeof document === 'undefined' || !document.body) return;

 const LINE_FIELDS = [['description', 'Beschreibung', 'wide'], ['quantity', 'Menge'], ['unit', 'Einheit'], ['unitPrice', 'Einzelpreis'], ['amount', 'Betrag'], ['packages', 'Packstücke'], ['netWeight', 'Netto kg'], ['grossWeight', 'Brutto kg']];
 const fieldLabel = (key, de) => tr('ds.field.' + key, de);

 async function open() {
  if (typeof activeLc === 'undefined' || !activeLc) return;
  const lcId = activeLc.id;
  const dialog = document.createElement('dialog'); dialog.className = 'wide-dialog document-set-dialog'; document.body.append(dialog); dialog.onclose = () => dialog.remove();
  dialog.innerHTML = `<div class="dialoghead"><h2>${tr('ds.title', 'Dokumentensatz erzeugen')}</h2><button type="button" data-close>${tr('common.close', 'Schließen')}</button></div><p role="status">${tr('common.preparing', 'Wird vorbereitet …')}</p>`;
  dialog.querySelector('[data-close]').onclick = () => dialog.close(); dialog.showModal();
  let proposal;
  try { proposal = await json(`/api/lcs/${encodeURIComponent(lcId)}/document-set/proposal`); } catch (error) { dialog.querySelector('[role=status]').textContent = error.message; return; }
  const previous = await json(`/api/lcs/${encodeURIComponent(lcId)}/document-set/previous`).catch(() => []);
  const mayReplace = typeof can === 'function' && can('DOCUMENT_DELETE');
  let base = '';
  const currency = proposal.currency || '';
  let lines = proposal.items.map(i => ({ description: i.description || '', quantity: i.quantity ?? '', unit: i.unit || '', unitPrice: i.unitPrice ?? '', amount: i.amount ?? '', packages: '', netWeight: '', grossWeight: '' }));
  let chosen = new Set(proposal.types);
  const render = () => {
   const rows = lines.map((line, i) => `<div class="ds-line" data-line="${i}">${LINE_FIELDS.map(([key, label, wide]) => `<label class="${wide || ''}">${esc(fieldLabel(key, label))}${key === 'description' ? `<textarea data-key="${key}" rows="2">${esc(line[key])}</textarea>` : `<input data-key="${key}" value="${esc(line[key])}" inputmode="decimal">`}</label>`).join('')}${lines.length > 1 ? `<button type="button" class="secondary" data-remove>${tr('ds.remove', 'Entfernen')}</button>` : ''}</div>`).join('');
   const boxes = proposal.requirements.map(r => `<label class="ds-type"><input type="checkbox" data-type="${esc(r.documentType)}"${chosen.has(r.documentType) ? ' checked' : ''}> ${esc(r.label)}${r.namedInCredit ? ` <small>${tr('ds.named', '(im Akkreditiv verlangt)')}</small>` : ''}${r.originals ? ` <small>· ${tr('ds.originals', '{n} Originale', { n: esc(r.originals) })}</small>` : ''}${r.signed ? ` <small>· ${tr('ds.signed', 'unterschrieben')}</small>` : ''}</label>`).join('');
   const total = limitMessage(totalOf(lines), proposal.maximumInvoiceTotal, currency);
   dialog.innerHTML = `<div class="dialoghead"><h2>${tr('ds.title', 'Dokumentensatz erzeugen')}</h2><button type="button" data-close>${tr('common.close', 'Schließen')}</button></div>` +
    `<p>${tr('ds.intro', 'Aus einer Eingabe entstehen alle gewählten Dokumente mit gleicher Nummer, gleichem Datum und gleichen Positionen. Danach werden sie gegen das Akkreditiv geprüft.')}</p>` +
    (proposal.companyConfigured === false ? `<p class="ds-hint" role="status">${tr('ds.noCompany', 'Für diese Akte ist keine Firma hinterlegt. Der Briefkopf zeigt nur den Begünstigten aus dem Akkreditiv. Anschrift, Logo und Bankverbindung kommen aus dem Firmenprofil (Mandanteneinstellungen → Firma).')}</p>` : '') +
    (previous.length ? `<div class="ds-base"><label>${tr('ds.base', 'Ausgangsbasis')}<select data-base><option value="">${tr('ds.baseNew', 'Neu aus dem Akkreditiv')}</option>${previous.map((p, i) => `<option value="${i}"${String(i) === base ? ' selected' : ''}>${esc(previousLabel(p))}</option>`).join('')}</select></label>` +
     (base !== '' && mayReplace ? `<label class="ds-check"><input type="checkbox" data-replace${state.replaceSetId ? ' checked' : ''}> ${tr('ds.replace', 'Alte Dokumente dieses Satzes ersetzen (werden gelöscht: {list})', { list: esc(previous[Number(base)].documents.join(', ')) })}</label>` : '') + '</div>' : '') +
    `<div class="ds-head"><label>${tr('ds.invoiceNumber', 'Rechnungsnummer')}<input data-field="invoiceNumber" value="${esc(proposal.invoiceNumber)}"></label><label>${tr('ds.documentDate', 'Dokumentdatum')}<input type="date" data-field="documentDate" value="${esc(proposal.documentDate)}"></label></div>` +
    `<h3>${tr('ds.documents', 'Dokumente')}</h3><div class="ds-types">${boxes}</div><h3>${tr('ds.lines', 'Positionen')}</h3>${rows}<button type="button" class="secondary" data-add>${tr('ds.addLine', '+ Position')}</button><p class="ds-total ds-${total.level}" role="status">${esc(total.text)}</p>` +
    `<label>${tr('ds.marks', 'Hinweise / Markierungen (Marks and numbers)')}<textarea data-field="notes" rows="2" maxlength="4000"></textarea></label><label>${tr('ds.format', 'Format')}<select data-field="format"><option value="docx">${tr('ds.word', 'Word (bearbeitbar)')}</option><option value="pdf">${tr('ds.pdf', 'PDF (fertig zum Versand)')}</option></select></label><p data-error role="alert"></p><div class="dialog-actions"><button type="button" data-create>${tr('ds.create', 'Dokumente erzeugen')}</button></div>`;
   dialog.querySelector('[data-close]').onclick = () => dialog.close();
   const baseSelect = dialog.querySelector('[data-base]');
   if (baseSelect) baseSelect.onchange = () => {
    base = baseSelect.value; state.replaceSetId = null;
    if (base === '') { Object.assign(state, { invoiceNumber: proposal.invoiceNumber, documentDate: proposal.documentDate, notes: '', packages: '', netWeight: '', grossWeight: '' }); lines = proposal.items.map(i => ({ description: i.description || '', quantity: i.quantity ?? '', unit: i.unit || '', unitPrice: i.unitPrice ?? '', amount: i.amount ?? '', packages: '', netWeight: '', grossWeight: '' })); chosen = new Set(proposal.types); }
    else { const loaded = fromPrevious(previous[Number(base)].request); Object.assign(state, loaded.state); lines = loaded.lines; chosen = new Set(loaded.types); }
    render();
   };
   const replace = dialog.querySelector('[data-replace]');
   if (replace) replace.onchange = () => { state.replaceSetId = replace.checked ? previous[Number(base)].setId : null; };
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
     if (!body.items.length) throw new Error(tr('ds.needLine', 'Bitte mindestens eine Position angeben.'));
     if (!body.types.length) throw new Error(tr('ds.needType', 'Bitte mindestens ein Dokument wählen.'));
     const result = await json(`/api/lcs/${encodeURIComponent(lcId)}/document-set`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
     dialog.innerHTML = `<div class="dialoghead"><h2>${tr('ds.done', 'Dokumentensatz erzeugt')}</h2><button type="button" data-close>${tr('common.close', 'Schließen')}</button></div>${resultHtml(result)}${(result.replaced || []).length ? `<p class="ds-hint">${tr('ds.replaced', 'Ersetzt und gelöscht')}: ${esc(result.replaced.join(', '))}</p>` : ''}`;
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
  button.textContent = tr('ds.title', 'Dokumentensatz erzeugen'); button.title = tr('ds.buttonTitle', 'Rechnung, Packliste und Zertifikate aus einer Eingabe erzeugen und gegen das Akkreditiv prüfen');
  button.onclick = open; upload.before(button);
 }
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; enhance(); }, 80); } }).observe(document.body, { childList: true, subtree: true });
 enhance();
})();
