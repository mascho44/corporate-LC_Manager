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

 const flash = node => { node.classList.remove('ld-flash'); void node.offsetWidth; node.classList.add('ld-flash'); };

 /** The original PDF page by page; every place of a finding is a numbered box on the page. `marks` collects them by finding number. */
 function pdfView(pages, rows, onMark) {
  const view = el('div', { className: 'ld-pdf', 'aria-label': tr('ld.pdf.title', 'Original-PDF mit Markierungen') }), marks = new Map();
  for (const page of pages) {
   const wrap = el('div', { className: 'ld-page', id: 'ldPage' + page.page }, el('img', { src: page.image, alt: tr('ld.pdf.pageAlt', 'Seite {p} des PDFs', { p: page.page }) }), el('span', { className: 'ld-pageno', textContent: tr('ld.pdf.pageNo', 'Seite {p}', { p: page.page }) }));
   rows.forEach((row, index) => (row.finding.evidence || []).filter(e => e.page === page.page).forEach(e => {
    const mark = el('button', { type: 'button', className: 'ld-mark-box ld-' + String(row.finding.severity).toLowerCase(), title: e.quote, 'aria-label': (row.finding.severity === 'OK' ? tr('ld.passed', 'Erfüllt') : String(index + 1)) + ': ' + e.quote }); mark.dataset.n = row.finding.severity === 'OK' ? '✓' : String(index + 1);
    Object.assign(mark.style, { left: e.x * 100 + '%', top: e.y * 100 + '%', width: e.w * 100 + '%', height: e.h * 100 + '%' });
    mark.onclick = () => onMark(index);
    wrap.append(mark); if (!marks.has(index)) marks.set(index, []); marks.get(index).push(mark);
   }));
   view.append(wrap);
  }
  return { view, marks };
 }

 /** Same look as the document check: counts as chips, then one row per check with a tick (met) or cross (wrong); beside it the original PDF with the findings marked. */
 function render(box, result) {
  box.replaceChildren();
  const sum = summary(result), problems = result.findings || [], passed = result.passed || [], pages = result.pages || [];
  const report = el('div', { className: 'ld-report' });
  const facts = [[tr('ld.f.reference', 'Referenz'), result.reference], [tr('ld.f.amount', 'Betrag'), result.amount != null ? result.amount + ' ' + (result.currency || '') : null], [tr('ld.f.expiry', 'Verfall'), result.expiryDate], [tr('ld.f.shipment', 'Verladung bis'), result.latestShipmentDate]].filter(f => f[1]);
  if (facts.length) { const grid = el('dl', { className: 'ld-facts' }); for (const [k, v] of facts) grid.append(el('div', {}, el('dt', { textContent: k }), el('dd', { textContent: v }))); report.append(grid); }
  report.append(el('div', { className: 'review-summary', role: 'status', 'aria-label': sum.text },
   el('span', { className: 'summary discrepancy', textContent: tr('rv.sumDiscrepancies', '{n} Abweichungen', { n: result.discrepancies }) }),
   el('span', { className: 'summary warning', textContent: tr('ld.sumWarnings', '{n} Hinweise', { n: result.warnings }) }),
   el('span', { className: 'summary passed', textContent: tr('rv.sumPassed', '{n} erfüllt', { n: passed.length }) })));
  report.append(el('p', { className: 'ld-verdict dc-' + (sum.level === 'bad' ? 'bad' : sum.level === 'ok' ? 'ok' : 'warning'), textContent: sum.text }));
  const rank = { DISCREPANCY: 0, WARNING: 1, INFO: 2, OK: 3 };
  const rows = [...problems, ...passed].sort((a, b) => (rank[a.severity] ?? 9) - (rank[b.severity] ?? 9)).map(finding => ({ finding }));
  const list = el('div', { className: 'check-list' }), nodes = [];
  let viewer = null;
  const show = index => { const marks = viewer?.marks.get(index); if (!marks?.length) return; marks[0].scrollIntoView({ block: 'center', behavior: 'smooth' }); marks.forEach(flash); };
  rows.forEach((row, index) => {
   const f = row.finding, [de, key] = LABEL[f.severity] || [f.severity, 'ld.warning'];
   const body = el('div', {}, el('b', { textContent: (f.severity === 'OK' ? tr('ld.passed', 'Erfüllt') : tr(key, de)) + ' · ' + (english() && f.messageEn ? f.messageEn : f.message) }));
   const advice = english() && f.adviceEn ? f.adviceEn : f.advice;
   if (advice) body.append(el('small', { className: 'check-source' }, el('strong', { textContent: tr('corp.fix', 'So beheben') }), document.createTextNode(advice)));
   const places = f.evidence || [];
   if (places.length && pages.length) body.append(el('div', { className: 'ld-evidence' }, el('button', { type: 'button', className: 'ld-jump secondary' + (f.severity === 'OK' ? ' ld-jump-ok' : ''), textContent: tr('ld.pdf.jump', '{n} · Im PDF, Seite {p} anzeigen', { n: f.severity === 'OK' ? '✓' : index + 1, p: [...new Set(places.map(e => e.page))].join(', ') }), onclick: () => show(index) }), el('small', { textContent: ' ' + places[0].quote })));
   const node = el('div', { className: 'check ' + String(f.severity).toLowerCase() }, el('span', { textContent: MARK[f.severity] || '!' }), body);
   nodes[index] = node; list.append(node);
  });
  report.append(list, el('p', { className: 'dc-note' }, el('small', { textContent: tr('ld.note', 'Automatische Vorprüfung ohne Vergleich mit dem Vertrag. Der Entwurf wurde nicht gespeichert.') })));
  if (!pages.length) { box.append(report); return; }
  viewer = pdfView(pages, rows, index => { nodes[index]?.scrollIntoView({ block: 'center', behavior: 'smooth' }); flash(nodes[index]); });
  box.append(el('div', { className: 'ld-split' }, report, viewer.view));
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
