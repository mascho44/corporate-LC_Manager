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

 function render(box, result) {
  box.replaceChildren();
  const sum = summary(result);
  box.append(el('p', { className: 'dc-' + (sum.level === 'bad' ? 'bad' : sum.level === 'ok' ? 'ok' : 'warning'), textContent: (sum.level === 'ok' ? '✓ ' : sum.level === 'bad' ? '✗ ' : '! ') + sum.text }));
  const read = [result.reference, result.amount != null ? result.amount + ' ' + (result.currency || '') : null, result.expiryDate ? tr('ld.expiry', 'Verfall {d}', { d: result.expiryDate }) : null, result.latestShipmentDate ? tr('ld.shipment', 'Verladung bis {d}', { d: result.latestShipmentDate }) : null].filter(Boolean).join(' · ');
  if (read) box.append(el('p', { className: 'dc-read', textContent: tr('ld.read', 'Gelesen: {text}', { text: read }) }));
  const list = el('ul', { className: 'dc-findings' });
  for (const f of result.findings || []) {
   const [de, key] = LABEL[f.severity] || [f.severity, 'ld.warning'];
   const item = el('li', { className: 'dc-' + String(f.severity).toLowerCase() }, el('b', { textContent: tr(key, de) + ': ' }), document.createTextNode(english() && f.messageEn ? f.messageEn : f.message));
   const advice = english() && f.adviceEn ? f.adviceEn : f.advice;
   if (advice) item.append(el('br'), el('small', { textContent: tr('corp.fix', 'So beheben') + ': ' + advice }));
   list.append(item);
  }
  if (list.children.length) box.append(list);
  box.append(el('p', { className: 'dc-note' }, el('small', { textContent: tr('ld.note', 'Automatische Vorprüfung ohne Vergleich mit dem Vertrag. Der Entwurf wurde nicht gespeichert.') })));
 }

 function open() {
  const dialog = document.querySelector('#lcDraftDialog') || el('dialog', { id: 'lcDraftDialog', className: 'wide-dialog draft-check-dialog' });
  const close = el('button', { type: 'button', className: 'ghost', textContent: '×' }); close.onclick = () => dialog.close();
  const text = el('textarea', { rows: 12, placeholder: ':20:...\n:31D:...\n:32B:...' }), file = el('input', { type: 'file', accept: '.txt,.pdf,text/plain,application/pdf' });
  const error = el('div', { className: 'error', role: 'alert' }), box = el('div'), run = el('button', { type: 'button', textContent: tr('ld.run', 'Entwurf prüfen') });
  run.onclick = async () => {
   error.textContent = ''; box.replaceChildren(); run.disabled = true;
   try {
    let result;
    if (file.files[0]) { const body = new FormData(); body.append('file', file.files[0]); result = await json('/api/lc-draft-check', { method: 'POST', body }); }
    else result = await json('/api/lc-draft-check', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text: text.value }) });
    render(box, result);
   } catch (e) { error.textContent = e.message; } finally { run.disabled = false; }
  };
  dialog.replaceChildren(
   el('div', { className: 'dialoghead' }, el('div', {}, el('h2', { textContent: tr('ld.title', 'LC-Entwurf prüfen') }), el('p', { textContent: tr('ld.intro', 'Den Entwurf des Akkreditivs (MT700-Text oder Datei) vor der Eröffnung auf Pflichtangaben, Fristen und riskante Klauseln prüfen.') })), close),
   el('label', {}, tr('ld.text', 'Entwurf als Text'), text), el('label', {}, tr('ld.file', 'oder als Datei (PDF oder Text)'), file), el('div', { className: 'actions' }, run), error, box);
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
