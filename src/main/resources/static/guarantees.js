/* Guarantees of the company (customer side, corporate profile): list, create and edit, status, and a block in the file of a guarantee. */
globalThis.LcGuarantees = {
 TYPES: ['ADVANCE_PAYMENT', 'PERFORMANCE', 'WARRANTY', 'BID', 'PAYMENT', 'CUSTOMS', 'RENT', 'OTHER'],
 STATUSES: ['REQUESTED', 'ACTIVE', 'RETURNED', 'CLAIMED', 'EXPIRED', 'CANCELLED'],
 REQUEST_STATUSES: ['DRAFT', 'SENT', 'QUERY', 'APPROVED', 'ISSUED', 'REJECTED', 'WITHDRAWN'],
 requestStatusName(s) { return tx('gar.req.status.' + s, s); },
 typeName(t) { return tx('gar.type.' + t, t); },
 statusName(s) { return tx('gar.status.' + s, s); },
 tone(g) { return !g.open ? 'done' : g.expiringSoon ? 'warn' : 'ok'; },
 money(value, currency) { return Number(value).toLocaleString(globalThis.LcI18n?.language?.() === 'en' ? 'en-GB' : 'de-DE', { style: 'currency', currency, maximumFractionDigits: 2 }); },
 day(value) { return value ? (typeof fmtDate === 'function' ? fmtDate(value) : value) : '–'; },
 node(tag, value, cls) { const n = document.createElement(tag); if (value !== undefined) n.textContent = value; if (cls) n.className = cls; return n; },
 /** One line with the facts of a guarantee. */
 summary(g) {
  return [g.bank && String(g.bank).split('\n')[0], g.beneficiary && tx('gar.for', 'für {name}', { name: String(g.beneficiary).split('\n')[0] }), g.currency && LcGuarantees.money(g.amount, g.currency),
   tx('gar.until', 'bis {date}', { date: g.expiryDate ? LcGuarantees.day(g.expiryDate) : tx('gar.noEnd', 'unbefristet') })].filter(Boolean).join(' · ');
 },
 /** The form for a new or an existing guarantee; done(saved) is called after saving. */
 form(guarantee, done, cancel) {
  const N = LcGuarantees.node;
  const f = document.createElement('form'); f.className = 'form-grid gar-form';
  const field = (label, control) => { const l = N('label', label); l.append(control); f.append(l); return control; };
  const input = (label, type, value, attrs = {}) => { const c = field(label, document.createElement('input')); c.type = type; if (value != null) c.value = value; Object.assign(c, attrs); return c; };
  const g = guarantee || {};
  const reference = input(tx('gar.reference', 'Referenz'), 'text', g.reference || '', { required: true, maxLength: 100 });
  const type = field(tx('gar.typeLabel', 'Art'), document.createElement('select')); LcGuarantees.TYPES.forEach(t => { const o = N('option', LcGuarantees.typeName(t)); o.value = t; type.append(o); }); type.value = g.guaranteeType || 'PERFORMANCE';
  const bank = input(tx('gar.bank', 'Ausstellende Bank'), 'text', g.bank || '', { required: true, maxLength: 500 });
  const beneficiary = input(tx('gar.beneficiary', 'Begünstigter'), 'text', g.beneficiary || '', { required: true, maxLength: 500 });
  const currency = input(tx('gar.currency', 'Währung'), 'text', g.currency || 'EUR', { required: true, maxLength: 3 });
  const amount = input(tx('gar.amount', 'Betrag'), 'number', g.amount != null ? g.amount : '', { required: true, min: '0.01', step: '0.01' });
  const issue = input(tx('gar.issue', 'Ausgestellt am'), 'date', g.issueDate || '');
  const validFrom = input(tx('gar.validFrom', 'Gültig ab'), 'date', g.validFrom || '');
  const expiry = input(tx('gar.expiry', 'Gültig bis (Ablauf)'), 'date', g.expiryDate || '');
  const claim = input(tx('gar.claimUntil', 'Inanspruchnahme bis'), 'date', g.claimUntil || '');
  const notice = input(tx('gar.notice', 'Entscheidung: Tage vor Ablauf'), 'number', g.extensionNoticeDays != null ? g.extensionNoticeDays : '', { min: '0', max: '730', step: '1' });
  const underlying = input(tx('gar.underlying', 'Grundgeschäft (Vertrag, Projekt)'), 'text', g.underlying || '', { maxLength: 500 });
  const beneficiaryReference = input(tx('gar.beneficiaryReference', 'Aktenzeichen des Begünstigten'), 'text', g.beneficiaryReference || '', { maxLength: 100 });
 const lineSelect = field(tx('gar.line', 'Linie'), document.createElement('select')); const noLine = N('option', tx('gar.lineNone', '(Bank frei eingeben)')); noLine.value = ''; lineSelect.append(noLine);
  (LcGuarantees.lines || []).filter(l => l.active).forEach(l => { const o = N('option', l.name + ' · ' + String(l.bank).split('\n')[0] + ' · ' + l.currency + ' · ' + tx('gar.lineUsed', '{percent} % belegt', { percent: l.percent })); o.value = l.id; lineSelect.append(o); });
  lineSelect.onchange = () => { const l = (LcGuarantees.lines || []).find(x => x.id === lineSelect.value); if (!l) return; bank.value = l.bank; currency.value = l.currency; if (l.guaranteeType) type.value = l.guaranteeType; bank.dispatchEvent(new Event('input')); };
  const hint = N('div', undefined, 'gar-check'); hint.setAttribute('role', 'status'); f.append(hint);
  let timer = null;
  const check = () => {
   clearTimeout(timer);
   timer = setTimeout(async () => {
    hint.replaceChildren(); hint.className = 'gar-check';
    if (!bank.value.trim() || currency.value.trim().length !== 3 || !amount.value) return;
    try {
     const q = new URLSearchParams({ bank: bank.value, currency: currency.value, type: type.value, amount: amount.value }); if (guarantee) q.set('exclude', guarantee.id);
     const c = await json('/api/credit-lines/check?' + q);
     if (!c.found) { hint.append(N('p', tx('cl.noLine', 'Für diese Bank und Währung ist keine Linie hinterlegt.'))); return; }
     const used = Number(c.limit) - Number(c.free), usedAfter = Number(c.limit) - Number(c.freeAfter);
     const pct = v => Math.round(v * 100 / Number(c.limit));
     const state = !c.fits || c.state === 'EXCEEDED' ? 'bad' : c.state === 'WARNING' ? 'warn' : 'ok';
     hint.className = 'gar-check ' + state;
     const bar = N('div', undefined, 'gar-bar gar-bar-pair ' + state); const before = N('span', undefined, 'gar-bar-before'); before.style.width = Math.min(100, pct(used)) + '%'; const added = N('span', undefined, 'gar-bar-added'); added.style.width = Math.max(0, Math.min(100, pct(usedAfter)) - Math.min(100, pct(used))) + '%'; bar.append(before, added);
     hint.append(N('p', tx('cl.checkLine', 'Linie „{name}“ ({limit}): belegt {before} % → mit dieser Garantie {after} %; frei {free}.', { name: c.lineName, limit: LcGuarantees.money(c.limit, c.currency), before: pct(used), after: pct(usedAfter), free: LcGuarantees.money(c.freeAfter, c.currency) })), bar);
     if (!c.fits) hint.append(N('p', tx('cl.exceeds', 'Achtung: Die Linie „{name}“ reicht nicht: frei {free}, es fehlen {missing}.', { name: c.lineName, free: LcGuarantees.money(c.free, c.currency), missing: LcGuarantees.money(-c.freeAfter, c.currency) })));
    } catch { /* the check is a hint only */ }
   }, 400);
  };
  [bank, currency, amount, type].forEach(c => { c.addEventListener('input', check); c.addEventListener('change', check); });
  if (guarantee) check();
  const auto = document.createElement('input'); auto.type = 'checkbox'; auto.checked = Boolean(g.autoExtension); const autoLabel = N('label', tx('gar.autoExtension', 'Verlängert sich automatisch')); autoLabel.prepend(auto); f.append(autoLabel);
  const save = N('button', tx('gar.save', 'Speichern')); save.type = 'submit'; const cancelButton = N('button', tx('gar.cancel', 'Abbrechen'), 'secondary'); cancelButton.type = 'button'; cancelButton.onclick = cancel;
  const message = N('p', ''); message.setAttribute('role', 'status'); const bar = N('div', undefined, 'gar-actions'); bar.append(save, cancelButton); f.append(bar, message);
  f.onsubmit = async event => {
   event.preventDefault();
   const body = { reference: reference.value, guaranteeType: type.value, bank: bank.value, beneficiary: beneficiary.value, currency: currency.value, amount: amount.value, issueDate: issue.value || null, validFrom: validFrom.value || null, expiryDate: expiry.value || null, claimUntil: claim.value || null,
    autoExtension: auto.checked, extensionNoticeDays: notice.value === '' ? null : Number(notice.value), underlying: underlying.value, beneficiaryReference: beneficiaryReference.value, status: g.status || 'ACTIVE' };
   try { done(await json(guarantee ? '/api/guarantees/' + guarantee.id : '/api/guarantees', { method: guarantee ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })); }
   catch (error) { message.textContent = error.message; }
  };
  return f;
 },
 /** The small form that changes the status. */
 statusForm(guarantee, done, cancel) {
  const N = LcGuarantees.node;
  const f = document.createElement('form'); f.className = 'form-grid gar-form';
  const field = (label, control) => { const l = N('label', label); l.append(control); f.append(l); return control; };
  const status = field(tx('gar.statusLabel', 'Neuer Status'), document.createElement('select')); LcGuarantees.STATUSES.forEach(s => { const o = N('option', LcGuarantees.statusName(s)); o.value = s; status.append(o); }); status.value = guarantee.status;
  const date = field(tx('gar.statusDate', 'Datum'), document.createElement('input')); date.type = 'date'; date.max = new Date().toISOString().slice(0, 10); date.value = date.max;
  const note = field(tx('gar.statusNote', 'Hinweis (optional)'), document.createElement('input')); note.maxLength = 500;
  const save = N('button', tx('gar.save', 'Speichern')); save.type = 'submit'; const cancelButton = N('button', tx('gar.cancel', 'Abbrechen'), 'secondary'); cancelButton.type = 'button'; cancelButton.onclick = cancel;
  const message = N('p', ''); message.setAttribute('role', 'status'); const bar = N('div', undefined, 'gar-actions'); bar.append(save, cancelButton); f.append(bar, message);
  f.onsubmit = async event => {
   event.preventDefault();
   try { done(await json('/api/guarantees/' + guarantee.id + '/status', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: status.value, date: date.value, note: note.value }) })); }
   catch (error) { message.textContent = error.message; }
  };
  return f;
 },
 /** The form for the text of the application to the bank. */
 requestForm(lcId, request, done, cancel) {
  const N = LcGuarantees.node;
  const f = document.createElement('form'); f.className = 'form-grid gar-form';
  const field = (label, control) => { const l = N('label', label); l.append(control); f.append(l); return control; };
  const recipient = field(tx('gar.req.recipient', 'Empfänger (Bank, Abteilung, Adresse)'), document.createElement('textarea')); recipient.rows = 3; recipient.maxLength = 500; recipient.value = request.recipient || '';
  const ourReference = field(tx('gar.req.ourReference', 'Unser Zeichen'), document.createElement('input')); ourReference.maxLength = 100; ourReference.value = request.ourReference || '';
  const due = field(tx('gar.req.responseDue', 'Antwort der Bank erwartet bis'), document.createElement('input')); due.type = 'date'; due.value = request.responseDue || '';
  const wording = field(tx('gar.req.wording', 'Gewünschter Wortlaut'), document.createElement('textarea')); wording.rows = 6; wording.maxLength = 8000; wording.value = request.wording || '';
  const remarks = field(tx('gar.req.remarks', 'Bemerkungen im Antrag'), document.createElement('textarea')); remarks.rows = 2; remarks.maxLength = 1000; remarks.value = request.remarks || '';
  const save = N('button', tx('gar.save', 'Speichern')); save.type = 'submit'; const cancelButton = N('button', tx('gar.cancel', 'Abbrechen'), 'secondary'); cancelButton.type = 'button'; cancelButton.onclick = cancel;
  const message = N('p', ''); message.setAttribute('role', 'status'); const bar = N('div', undefined, 'gar-actions'); bar.append(save, cancelButton); f.append(bar, message);
  f.onsubmit = async event => {
   event.preventDefault();
   try { await json('/api/guarantees/' + lcId + '/request', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ recipient: recipient.value, ourReference: ourReference.value, wording: wording.value, remarks: remarks.value, responseDue: due.value || null }) }); done(); }
   catch (error) { message.textContent = error.message; }
  };
  return f;
 },
 /** The form that moves the application to its next status. */
 requestStatusForm(lcId, request, done, cancel) {
  const N = LcGuarantees.node;
  const f = document.createElement('form'); f.className = 'form-grid gar-form';
  const field = (label, control) => { const l = N('label', label); l.append(control); f.append(l); return control; };
  const status = field(tx('gar.req.newStatus', 'Neuer Status des Antrags'), document.createElement('select')); request.nextStatuses.forEach(x => { const o = N('option', LcGuarantees.requestStatusName(x)); o.value = x; status.append(o); });
  const date = field(tx('gar.statusDate', 'Datum'), document.createElement('input')); date.type = 'date'; date.max = new Date().toISOString().slice(0, 10); date.value = date.max;
  const bankReference = field(tx('gar.req.bankReference', 'Referenz der Bank (optional)'), document.createElement('input')); bankReference.maxLength = 100; bankReference.value = request.bankReference || '';
  const note = field(tx('gar.statusNote', 'Hinweis (optional)'), document.createElement('input')); note.maxLength = 500;
  const hint = N('p', tx('gar.req.statusHint', 'Ausgestellt macht die Garantie aktiv; abgelehnt oder zurückgezogen schließt sie als „nicht zustande gekommen“.'), 'gar-check'); f.append(hint);
  const save = N('button', tx('gar.save', 'Speichern')); save.type = 'submit'; const cancelButton = N('button', tx('gar.cancel', 'Abbrechen'), 'secondary'); cancelButton.type = 'button'; cancelButton.onclick = cancel;
  const message = N('p', ''); message.setAttribute('role', 'status'); const bar = N('div', undefined, 'gar-actions'); bar.append(save, cancelButton); f.append(bar, message);
  f.onsubmit = async event => {
   event.preventDefault();
   try { await json('/api/guarantees/' + lcId + '/request/status', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: status.value, date: date.value, note: note.value, bankReference: bankReference.value }) }); done(); }
   catch (error) { message.textContent = error.message; }
  };
  return f;
 },
 async downloadRequest(lcId, language, reference) {
  const response = await jsonRaw('/api/guarantees/' + lcId + '/request/document?language=' + language, { method: 'POST' });
  const blob = await response.blob(); const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = 'Garantieantrag-' + (reference || 'Garantie') + '.docx'; document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(link.href), 1000);
 },
 /** The block in the file of a guarantee: facts, deadlines hint, change and status buttons. */
 async render(container, lcId) {
  document.body.classList.toggle('guarantee-file', typeof activeLc !== 'undefined' && activeLc?.instrumentType === 'GUARANTEE');
  if (typeof activeLc !== 'undefined' && activeLc && activeLc.instrumentType && activeLc.instrumentType !== 'GUARANTEE') return;
  let g; try { g = await json('/api/guarantees/' + encodeURIComponent(lcId)); } catch { return; }
  if (!container.isConnected) return;
  const N = LcGuarantees.node;
  container.querySelector('.guarantee-block')?.remove();
  const block = N('section', undefined, 'guarantee-block sla-block');
  const head = N('h3', tx('gar.title', 'Garantie')); head.append(' ', N('span', LcGuarantees.statusName(g.status), 'sla-badge ' + LcGuarantees.tone(g))); block.append(head);
  const lines = [LcGuarantees.typeName(g.guaranteeType) + ' · ' + LcGuarantees.summary(g)];
  if (g.claimUntil) lines.push(tx('gar.claimLine', 'Inanspruchnahme möglich bis {date}', { date: LcGuarantees.day(g.claimUntil) }));
  if (g.decisionDate && g.open) lines.push(tx('gar.decisionLine', 'Entscheiden bis {date}: {what}', { date: LcGuarantees.day(g.decisionDate), what: g.autoExtension ? tx('gar.decisionAuto', 'Verlängerung verhindern oder Rückgabe verlangen') : tx('gar.decisionManual', 'Verlängerung beantragen oder Rückgabe verlangen') }));
  if (g.bankReference) lines.push(tx('gar.bankRefLine', 'Referenz der Bank: {ref}', { ref: g.bankReference }));
  if (g.underlying) lines.push(tx('gar.underlyingLine', 'Grundgeschäft: {text}', { text: g.underlying }));
  if (g.statusNote) lines.push(g.statusNote);
  if (!g.detailsKnown) lines.push(tx('gar.detailsMissing', 'Für diese Garantie fehlen noch Angaben (Art, Fristen). Bitte ergänzen.'));
  lines.forEach(l => block.append(N('p', l)));
  let request = null; try { request = await json('/api/guarantees/' + encodeURIComponent(lcId) + '/request'); } catch { request = null; }
  const showRequest = request && (request.exists || g.status === 'REQUESTED');
  const mayEdit = typeof can === 'function' && can('LC_EDIT');
  if (showRequest) {
   block.append(N('h4', tx('gar.req.title', 'Antrag bei der Bank')));
   const rl = [LcGuarantees.requestStatusName(request.status)];
   if (request.sentOn) rl.push(tx('gar.req.sentOn', 'gesendet am {date}', { date: LcGuarantees.day(request.sentOn) }));
   if (request.bankReference) rl.push(tx('gar.req.bankRef', 'Referenz der Bank {ref}', { ref: request.bankReference }));
   if (request.responseDue && request.open && request.status !== 'DRAFT') rl.push(tx('gar.req.answerBy', 'Antwort erwartet bis {date}', { date: LcGuarantees.day(request.responseDue) }));
   block.append(N('p', rl.join(' · ')));
   if (request.statusNote) block.append(N('p', request.statusNote));
  }
  if (typeof can === 'function' && can('LC_EDIT')) {
   const slot = N('div', undefined, 'gar-slot');
   const buttons = N('div', undefined, 'gar-buttons');
   const again = () => LcGuarantees.render(container, lcId);
   const edit = N('button', tx('gar.edit', 'Garantie bearbeiten'), 'secondary'); edit.type = 'button'; edit.onclick = () => { slot.replaceChildren(LcGuarantees.form(g, again, () => slot.replaceChildren())); };
   const status = N('button', tx('gar.changeStatus', 'Status ändern'), 'secondary'); status.type = 'button'; status.onclick = () => { slot.replaceChildren(LcGuarantees.statusForm(g, again, () => slot.replaceChildren())); };
   buttons.append(edit, status);
   if (showRequest && request.open) {
    const reqEdit = N('button', tx('gar.req.edit', 'Antrag bearbeiten'), 'secondary'); reqEdit.type = 'button'; reqEdit.onclick = () => { slot.replaceChildren(LcGuarantees.requestForm(lcId, request, again, () => slot.replaceChildren())); };
    const word = N('button', tx('gar.req.word', 'Antrag als Word (Deutsch)'), 'secondary'); word.type = 'button'; word.onclick = async () => { try { await LcGuarantees.downloadRequest(lcId, 'de', g.reference); } catch (error) { alert(error.message); } };
    const wordEn = N('button', tx('gar.req.wordEn', 'Antrag als Word (Englisch)'), 'secondary'); wordEn.type = 'button'; wordEn.onclick = async () => { try { await LcGuarantees.downloadRequest(lcId, 'en', g.reference); } catch (error) { alert(error.message); } };
    const reqStatus = N('button', tx('gar.req.changeStatus', 'Antragsstatus ändern'), 'secondary'); reqStatus.type = 'button'; reqStatus.onclick = () => { slot.replaceChildren(LcGuarantees.requestStatusForm(lcId, request, again, () => slot.replaceChildren())); };
    buttons.append(reqEdit, word, wordEn, reqStatus);
   }
   block.append(buttons, slot);
  }
  // copy of the instrument or of the bank's advice (PDF), as received from the bank
  const copies = await json('/api/lcs/' + encodeURIComponent(lcId) + '/documents').then(all => all.filter(d => d.documentType === 'GUARANTEE_COPY')).catch(() => []);
  block.append(N('h4', tx('gar.copy.title', 'Garantieurkunde (Kopie)')));
  if (!copies.length) block.append(N('p', tx('gar.copy.none', 'Noch keine Kopie der Garantie oder des Avis abgelegt.')));
  copies.forEach(d => { const p = N('p'); const a = N('a', d.originalFilename); a.href = d.downloadUrl; a.target = '_blank'; a.rel = 'noopener'; p.append(a, ' · ' + String(d.uploadedAt || '').slice(0, 10)); block.append(p); });
  if (copies.length) {
   const check = await json('/api/guarantees/' + encodeURIComponent(lcId) + '/copy-check').catch(() => null);
   if (check && check.items) {
    const names = { currency: tx('gar.cc.currency', 'Währung'), amount: tx('gar.cc.amount', 'Betrag'), beneficiary: tx('gar.cc.beneficiary', 'Begünstigter'), bank: tx('gar.cc.bank', 'Bank'), expiry: tx('gar.cc.expiry', 'Laufzeit bis') };
    const marks = { OK: '✓', DEVIATION: '✗', UNKNOWN: '?' };
    const list = N('ul', undefined, 'gar-copy-check');
    check.items.forEach(i => list.append(N('li', marks[i.result] + ' ' + names[i.field] + (i.expected ? ': ' + i.expected : '') + (i.result === 'DEVIATION' && i.found ? ' – ' + tx('gar.cc.found', 'im Text: {value}', { value: i.found }) : '') + (i.result === 'UNKNOWN' ? ' – ' + tx('gar.cc.unknown', 'im Text nicht erkennbar') : ''), 'gar-cc-' + i.result.toLowerCase())));
    if (check.foundReference && check.foundReference !== check.bankReference && mayEdit) {
     const row = N('p', tx('gar.cc.refFound', 'Referenz der Bank in der Kopie: {ref}', { ref: check.foundReference }) + ' ');
     const take = N('button', tx('gar.cc.refTake', 'In die Akte übernehmen'), 'secondary'); take.type = 'button';
     take.onclick = async () => { try { await json('/api/guarantees/' + encodeURIComponent(lcId) + '/bank-reference', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ bankReference: check.foundReference }) }); LcGuarantees.render(container, lcId); } catch (error) { row.append(' ' + error.message); } };
     row.append(take); block.append(row);
    }
    block.append(N('p', check.textRead ? tx('gar.cc.title', 'Abgleich der Kopie mit den Garantiedaten (Texterkennung, nur ein Hinweis):') : tx('gar.cc.noText', 'Aus der Kopie konnte kein Text gelesen werden; kein Abgleich möglich.')), list);
   }
  }
  if (mayEdit) {
   const input = document.createElement('input'); input.type = 'file'; input.accept = '.pdf,application/pdf'; input.hidden = true;
   const upload = N('button', tx('gar.copy.upload', 'Kopie der Garantie ablegen (PDF)'), 'secondary'); upload.type = 'button'; upload.onclick = () => input.click();
   const failed = N('p', ''); failed.setAttribute('role', 'alert');
   input.onchange = async () => {
    const file = input.files[0]; if (!file) return;
    const form = new FormData(); form.append('file', file);
    try { await json('/api/lcs/' + encodeURIComponent(lcId) + '/documents?type=GUARANTEE_COPY', { method: 'POST', body: form }); LcGuarantees.render(container, lcId); }
    catch (error) { failed.textContent = tx('gar.copy.failed', 'Kopie konnte nicht abgelegt werden') + ': ' + error.message; }
   };
   block.append(upload, input, failed);
  }
  container.querySelector('.grid')?.before(block) || container.append(block);
 }
};

async function setupGuarantees() {
 let list; try { list = await json('/api/guarantees'); } catch { return; } // not a corporate tenant: no page
 const N = LcGuarantees.node;
 try { LcGuarantees.lines = (await json('/api/credit-lines')).lines; } catch { LcGuarantees.lines = []; }
 const nav = N('button', tx('gar.nav', 'Garantien')); nav.type = 'button'; nav.id = 'appNavGuarantees'; nav.dataset.i18n = 'gar.nav';
 document.querySelector('#appNav nav').append(nav);
 const section = document.createElement('section'); section.id = 'guaranteeSection'; section.className = 'panel hidden';
 const message = N('p', ''); message.setAttribute('role', 'status');
 const filters = N('div', undefined, 'gar-filters'); const rows = document.createElement('div'); const formBox = document.createElement('div');
 section.append(N('h2', tx('gar.pageTitle', 'Garantien')), N('p', tx('gar.intro', 'Garantien, die Ihre Banken für Sie ausgestellt haben: Bestand, Ablauf, Fristen für Inanspruchnahme und Verlängerung. Rückgabe oder Inanspruchnahme vermerken Sie am Status; zurückgegebene Garantien erscheinen nicht mehr in den Fristen.')), message, filters, rows, formBox);
 document.querySelector('main').append(section);
 let view = 'OPEN', query = '';
 const matches = g => (view === 'ALL' || (view === 'OPEN' ? g.open : view === 'SOON' ? g.expiringSoon : !g.open)) && (!query || [g.reference, g.bank, g.beneficiary, g.underlying, g.beneficiaryReference].some(v => v && String(v).toLowerCase().includes(query)));
 async function reload() { try { [list, LcGuarantees.lines] = await Promise.all([json('/api/guarantees'), json('/api/credit-lines').then(o => o.lines)]); } catch (error) { message.textContent = error.message; } draw(); }
 function draw() {
  filters.replaceChildren(); rows.replaceChildren();
  const select = document.createElement('select');
  [['OPEN', tx('gar.f.open', 'Offene')], ['SOON', tx('gar.f.soon', 'Laufen in 90 Tagen ab')], ['CLOSED', tx('gar.f.closed', 'Erledigte')], ['ALL', tx('gar.f.all', 'Alle')]].forEach(([value, text]) => { const o = N('option', text); o.value = value; select.append(o); });
  select.value = view; select.onchange = () => { view = select.value; draw(); };
  const search = document.createElement('input'); search.type = 'search'; search.placeholder = tx('gar.search', 'Suchen (Referenz, Bank, Begünstigter)'); search.value = query; search.oninput = () => { query = search.value.trim().toLowerCase(); drawRows(); };
  filters.append(select, search);
  if (typeof can === 'function' && can('LC_EDIT')) { const add = N('button', tx('gar.add', 'Garantie anlegen')); add.type = 'button'; add.onclick = () => { formBox.replaceChildren(LcGuarantees.form(null, () => { formBox.replaceChildren(); message.textContent = tx('gar.saved', 'Garantie gespeichert.'); reload(); }, () => formBox.replaceChildren())); formBox.scrollIntoView(); }; filters.append(add); }
  drawRows();
 }
 function drawRows() {
  rows.replaceChildren();
  const shown = list.filter(matches);
  if (!shown.length) { rows.append(N('p', list.length ? tx('gar.noneShown', 'Keine Garantien in dieser Ansicht.') : tx('gar.none', 'Noch keine Garantien.'))); return; }
  shown.forEach(g => {
   const row = N('article', undefined, 'membership-row gar-row' + (g.open ? '' : ' inactive'));
   const text = N('div'); const title = N('b', g.reference + ' · ' + LcGuarantees.typeName(g.guaranteeType) + ' '); title.append(N('span', LcGuarantees.statusName(g.status), 'sla-badge ' + LcGuarantees.tone(g)));
   text.append(title, N('small', LcGuarantees.summary(g)));
   const extra = []; if (g.open && g.daysToExpiry != null) extra.push(g.daysToExpiry < 0 ? tx('gar.expired', 'abgelaufen seit {n} Tagen', { n: -g.daysToExpiry }) : tx('gar.daysLeft', 'noch {n} Tage', { n: g.daysToExpiry }));
   if (g.open && g.decisionDate) extra.push(tx('gar.decideBy', 'Entscheiden bis {date}', { date: LcGuarantees.day(g.decisionDate) }));
   if (g.open && g.autoExtension) extra.push(tx('gar.autoShort', 'verlängert sich automatisch'));
   if (extra.length) text.append(N('small', extra.join(' · ')));
   const open = N('button', tx('gar.openFile', 'Akte öffnen'), 'secondary'); open.type = 'button';
   open.onclick = async () => { appNavigate('lcs', document.querySelector('#appNavGuarantees')); await show(g.id); };
   row.append(text, open); rows.append(row);
  });
 }
 nav.onclick = () => { appNavigate('guarantees', nav); reload(); };
 draw();
}

async function setupCreditLines() {
 let overview; try { overview = await json('/api/credit-lines'); } catch { return; } // not a corporate tenant: no page
 const N = LcGuarantees.node;
 const nav = N('button', tx('cl.nav', 'Linien')); nav.type = 'button'; nav.id = 'appNavCreditLines'; nav.dataset.i18n = 'cl.nav';
 document.querySelector('#appNav nav').append(nav);
 const section = document.createElement('section'); section.id = 'creditLineSection'; section.className = 'panel hidden';
 const message = N('p', ''); message.setAttribute('role', 'status');
 const linesBox = N('div', undefined, 'gar-lines'); const formBox = document.createElement('div');
 section.append(N('h2', tx('cl.title', 'Linien und Auslastung')), N('p', tx('cl.intro', 'Rahmen, die Ihre Banken Ihnen für Garantien eingeräumt haben, je Bank und Währung, optional je Garantieart. Offene Garantien belegen die Linie ihrer Bank; zurückgegebene, in Anspruch genommene und abgelaufene geben sie frei. Garantien legen Sie unter „Garantien“ an; dort sehen Sie die Auslastung schon beim Erfassen.')), message, linesBox, formBox);
 document.querySelector('main').append(section);
 const canLines = () => typeof can === 'function' && can('SETTINGS_MANAGE');
 async function reload() { try { overview = await json('/api/credit-lines'); LcGuarantees.lines = overview.lines; } catch (error) { message.textContent = error.message; } drawLines(); }
 const lineForm = line => {
  const f = document.createElement('form'); f.className = 'form-grid gar-form';
  const field = (label, control) => { const l = N('label', label); l.append(control); f.append(l); return control; };
  const input = (label, type, value, attrs = {}) => { const c = field(label, document.createElement('input')); c.type = type; if (value != null) c.value = value; Object.assign(c, attrs); return c; };
  const l = line || {};
  const name = input(tx('cl.name', 'Name der Linie'), 'text', l.name || '', { required: true, maxLength: 120 });
  const bank = input(tx('cl.bank', 'Bank (wie bei den Garantien eingetragen)'), 'text', l.bank || '', { required: true, maxLength: 200 });
  const bic = input(tx('cl.bic', 'BIC (optional, hilft beim Zuordnen)'), 'text', l.bic || '', { maxLength: 11 });
  const currency = input(tx('cl.currency', 'Währung'), 'text', l.currency || 'EUR', { required: true, maxLength: 3 });
  const amount = input(tx('cl.amount', 'Betrag der Linie'), 'number', l.amount != null ? l.amount : '', { required: true, min: '0.01', step: '0.01' });
  const type = field(tx('cl.type', 'Garantieart'), document.createElement('select')); const all = N('option', tx('cl.allTypes', 'alle Arten')); all.value = ''; type.append(all); LcGuarantees.TYPES.forEach(t => { const o = N('option', LcGuarantees.typeName(t)); o.value = t; type.append(o); }); type.value = l.guaranteeType || '';
  const until = input(tx('cl.validUntil', 'Gültig bis (optional)'), 'date', l.validUntil || '');
  const warn = input(tx('cl.warn', 'Warnung ab (%)'), 'number', l.warnPercent != null ? l.warnPercent : 80, { min: '10', max: '99', step: '1' });
  const note = input(tx('cl.note', 'Hinweis (optional)'), 'text', l.note || '', { maxLength: 500 });
  const active = document.createElement('input'); active.type = 'checkbox'; active.checked = line ? line.active : true; const activeLabel = N('label', tx('cl.active', 'Aktiv')); activeLabel.prepend(active); f.append(activeLabel);
  const save = N('button', tx('gar.save', 'Speichern')); save.type = 'submit'; const cancel = N('button', tx('gar.cancel', 'Abbrechen'), 'secondary'); cancel.type = 'button'; cancel.onclick = () => formBox.replaceChildren();
  const msg = N('p', ''); msg.setAttribute('role', 'status'); const bar = N('div', undefined, 'gar-actions'); bar.append(save, cancel); f.append(bar, msg);
  f.onsubmit = async event => {
   event.preventDefault();
   const body = { name: name.value, bank: bank.value, bic: bic.value, currency: currency.value, amount: amount.value, guaranteeType: type.value || null, validUntil: until.value || null, warnPercent: Number(warn.value), active: active.checked, note: note.value };
   try { await json(line ? '/api/credit-lines/' + line.id : '/api/credit-lines', { method: line ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }); formBox.replaceChildren(); message.textContent = tx('cl.saved', 'Linie gespeichert.'); reload(); }
   catch (error) { msg.textContent = error.message; }
  };
  return f;
 };
 function drawLines() {
  linesBox.replaceChildren();
   if (!overview.lines.length) linesBox.append(N('p', tx('cl.none', 'Noch keine Linien. Mit einer Linie je Bank und Währung sehen Sie, wie viel Rahmen die laufenden Garantien belegen.')));
  overview.lines.forEach(l => {
   const row = N('article', undefined, 'membership-row gar-line' + (l.active ? '' : ' inactive'));
   const text = N('div'); const title = N('b', l.name + ' · ' + l.bank + ' · ' + l.currency + (l.guaranteeType ? ' · ' + LcGuarantees.typeName(l.guaranteeType) : '') + ' ');
   const badge = l.state === 'EXCEEDED' ? [tx('cl.s.exceeded', 'überschritten'), 'bad'] : l.state === 'WARNING' ? [tx('cl.s.warning', 'knapp'), 'warn'] : [tx('cl.s.ok', 'im Rahmen'), 'ok'];
   title.append(N('span', badge[0], 'sla-badge ' + badge[1])); if (l.expired) title.append(' ', N('span', tx('cl.expired', 'abgelaufen'), 'sla-badge bad')); if (!l.active) title.append(' ', N('span', tx('gar.inactive', 'inaktiv'), 'sla-badge'));
   const bar = N('div', undefined, 'gar-bar ' + badge[1]); const fill = N('span'); fill.style.width = Math.min(100, l.percent) + '%'; bar.append(fill);
   text.append(title, bar, N('small', tx('cl.usage', '{used} von {limit} belegt ({percent} %) · frei {free} · {n} Garantien', { used: LcGuarantees.money(l.used, l.currency), limit: LcGuarantees.money(l.amount, l.currency), percent: l.percent, free: LcGuarantees.money(l.free, l.currency), n: l.guaranteeCount })));
   row.append(text);
   if (canLines()) {
    const edit = N('button', tx('gar.editShort', 'Bearbeiten'), 'secondary'); edit.type = 'button'; edit.onclick = () => { formBox.replaceChildren(lineForm(l)); formBox.scrollIntoView(); };
    const del = N('button', tx('cl.delete', 'Löschen'), 'secondary'); del.type = 'button';
    del.onclick = async () => { if (!await confirmAction(tx('cl.confirmDelete', 'Linie „{name}“ löschen?', { name: l.name }))) return; try { await json('/api/credit-lines/' + l.id, { method: 'DELETE' }); message.textContent = tx('cl.deleted', 'Linie gelöscht.'); reload(); } catch (error) { message.textContent = error.message; } };
    row.append(edit, del);
   }
   linesBox.append(row);
  });
  if (overview.withoutLine.length) linesBox.append(N('p', tx('cl.without', 'Ohne Linie: {list}', { list: overview.withoutLine.map(g => g.reference + ' (' + String(g.bank || '–').split('\n')[0] + ', ' + g.currency + ')').join('; ') }), 'gar-without'));
  if (canLines()) { const add = N('button', tx('cl.add', 'Linie anlegen'), 'secondary'); add.type = 'button'; add.onclick = () => { formBox.replaceChildren(lineForm(null)); formBox.scrollIntoView(); }; linesBox.append(add); }
 }
 nav.onclick = () => { appNavigate('creditLines', nav); reload(); };
 drawLines();
}
