/* "LC-Entwurf prüfen": the draft of a credit that a third party (usually the issuing bank) sends before the credit is issued.
   The text (pasted MT700 or a file) is checked on the server for mandatory fields, plausible dates and risky clauses; nothing is stored. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };
 const english = () => globalThis.LcI18n?.language?.() === 'en';
 const LABEL = { DISCREPANCY: ['Abweichung', 'ld.discrepancy'], WARNING: ['Hinweis', 'ld.warning'], INFO: ['Information', 'ld.info'] };

 /** Summary line for a result: counts of discrepancies and warnings, or the all-clear. */
 function summary(result) {
  if (result.discrepancies) return { level: 'bad', text: tr('ld.bad', '{n} Abweichung(en) – vor der Eröffnung korrigieren lassen.', { n: result.discrepancies }) };
  if (result.warnings) return { level: 'warn', text: tr('ld.warn', 'Keine Abweichung, aber {n} Hinweis(e) bitte prüfen.', { n: result.warnings }) };
  return { level: 'ok', text: tr('ld.ok', 'Keine Auffälligkeit gefunden.') };
 }
 globalThis.LcDraftReview = { summary };
 if (typeof document === 'undefined' || !document.body) return;

 const el = (tag, props = {}, ...children) => { const node = Object.assign(document.createElement(tag), props); node.append(...children); return node; };

 const MARK = { OK: '✓', WARNING: '!', DISCREPANCY: '×', INFO: 'i' };

 /** Where the finding stands in the PDF: page and line as printed, the picture of the line with its marking on click. */
 function evidence(place) {
  const box = el('div', { className: 'ld-evidence' }, el('small', {}, el('strong', { textContent: tr('ld.pdf.page', 'Im PDF, Seite {p}', { p: place.page }) + ': ' }), document.createTextNode(place.quote)));
  if (place.image) box.append(el('details', {}, el('summary', { textContent: tr('ld.pdf.show', 'Stelle im PDF ansehen') }), el('img', { src: place.image, alt: tr('ld.pdf.alt', 'Ausschnitt des PDFs mit Markierung'), loading: 'lazy' })));
  return box;
 }

 /** Same look as the document check: counts as chips, then one row per check with a tick (met) or cross (wrong). */
 function render(box, result) {
  box.replaceChildren();
  const sum = summary(result), problems = result.findings || [], passed = result.passed || [];
  const facts = [[tr('ld.f.reference', 'Referenz'), result.reference], [tr('ld.f.amount', 'Betrag'), result.amount != null ? result.amount + ' ' + (result.currency || '') : null], [tr('ld.f.expiry', 'Verfall'), result.expiryDate], [tr('ld.f.shipment', 'Verladung bis'), result.latestShipmentDate]].filter(f => f[1]);
  if (facts.length) { const grid = el('dl', { className: 'ld-facts' }); for (const [k, v] of facts) grid.append(el('div', {}, el('dt', { textContent: k }), el('dd', { textContent: v }))); box.append(grid); }
  box.append(el('div', { className: 'review-summary', role: 'status', 'aria-label': sum.text },
   el('span', { className: 'summary discrepancy', textContent: tr('rv.sumDiscrepancies', '{n} Abweichungen', { n: result.discrepancies }) }),
   el('span', { className: 'summary warning', textContent: tr('ld.sumWarnings', '{n} Hinweise', { n: result.warnings }) }),
   el('span', { className: 'summary passed', textContent: tr('rv.sumPassed', '{n} erfüllt', { n: passed.length }) })));
  box.append(el('p', { className: 'ld-verdict dc-' + (sum.level === 'bad' ? 'bad' : sum.level === 'ok' ? 'ok' : 'warning'), textContent: sum.text }));
  const rank = { DISCREPANCY: 0, WARNING: 1, INFO: 2, OK: 3 }, rows = [...problems, ...passed].sort((a, b) => (rank[a.severity] ?? 9) - (rank[b.severity] ?? 9));
  const list = el('div', { className: 'check-list' });
  for (const f of rows) {
   const [de, key] = LABEL[f.severity] || [f.severity, 'ld.warning'];
   const body = el('div', {}, el('b', { textContent: (f.severity === 'OK' ? tr('ld.passed', 'Erfüllt') : tr(key, de)) + ' · ' + (english() && f.messageEn ? f.messageEn : f.message) }));
   const advice = english() && f.adviceEn ? f.adviceEn : f.advice;
   if (advice) body.append(el('small', { className: 'check-source' }, el('strong', { textContent: tr('corp.fix', 'So beheben') }), document.createTextNode(advice)));
   for (const place of f.evidence || []) body.append(evidence(place));
   list.append(el('div', { className: 'check ' + String(f.severity).toLowerCase() }, el('span', { textContent: MARK[f.severity] || '!' }), body));
  }
  box.append(list, el('p', { className: 'dc-note' }, el('small', { textContent: tr('ld.note', 'Automatische Vorprüfung ohne Vergleich mit dem Vertrag. Der Entwurf wurde nicht gespeichert.') })));
 }

 function open() {
  const dialog = document.querySelector('#lcDraftDialog') || el('dialog', { id: 'lcDraftDialog', className: 'wide-dialog draft-check-dialog' });
  const close = el('button', { type: 'button', className: 'ghost', textContent: '×' }); close.onclick = () => dialog.close();
  const file = el('input', { type: 'file', accept: '.pdf,application/pdf' }), text = el('textarea', { rows: 10, placeholder: ':20:...\n:31D:...\n:32B:...' });
  const error = el('div', { className: 'error', role: 'alert' }), box = el('div', { className: 'ld-result' });
  const send = async init => {
   error.textContent = ''; box.replaceChildren(el('p', { className: 'dc-note', textContent: tr('ld.working', 'Entwurf wird gelesen und geprüft …') }));
   try { render(box, await json('/api/lc-draft-check', init)); } catch (e) { box.replaceChildren(); error.textContent = e.message; }
  };
  file.onchange = () => { if (!file.files[0]) return; const body = new FormData(); body.append('file', file.files[0]); send({ method: 'POST', body }); };
  const run = el('button', { type: 'button', className: 'secondary', textContent: tr('ld.run', 'Text prüfen') });
  run.onclick = () => { if (!text.value.trim()) { error.textContent = tr('ld.choose', 'Bitte das PDF des Entwurfs wählen.'); return; } send({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text: text.value }) }); };
  const alternative = el('details', {}, el('summary', { textContent: tr('lf.draft.pasteText', 'Stattdessen Text einfügen') }), el('label', {}, tr('ld.text', 'Entwurf als Text'), text), el('div', { className: 'actions' }, run));
  dialog.replaceChildren(
   el('div', { className: 'dialoghead' }, el('div', {}, el('h2', { textContent: tr('ld.title', 'LC-Entwurf prüfen') }), el('p', { textContent: tr('ld.intro', 'PDF des Entwurfs wählen: Er wird sofort auf Pflichtangaben, Fristen und riskante Klauseln geprüft.') })), close),
   el('label', {}, tr('lf.draft.pdf', 'Entwurf als PDF'), file), alternative, error, box);
  if (!dialog.isConnected) document.body.append(dialog);
  dialog.showModal();
 }

 function build() {
  const anchor = document.querySelector('#appNavImport');
  if (!anchor || document.querySelector('#appNavLcDraft')) return;
  const nav = el('button', { id: 'appNavLcDraft', type: 'button', textContent: tr('ld.nav', 'LC-Entwurf prüfen') }); nav.onclick = open; anchor.after(nav);
 }
 let tries = 0;
 const wait = setInterval(() => { tries++; if (document.querySelector('#appNavImport')) { clearInterval(wait); build(); } else if (tries > 60) clearInterval(wait); }, 500);
})();
