const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const dir = 'src/main/resources/static/';
const FILES = ['app', 'lc-tools', 'procurement-plan', 'corporate-profile', 'kpis', 'akte-copy', 'draft-check', 'amendment-request', 'document-set', 'submission-package'];
const en = JSON.parse(fs.readFileSync(dir + 'language-en.json', 'utf8')), de = JSON.parse(fs.readFileSync(dir + 'language-de.json', 'utf8'));
const placeholders = text => [...String(text).matchAll(/\{(\w+)\}/g)].map(m => m[1]).sort().join(',');

test('every text of the corporate workflow has German and English with the same placeholders', () => {
 for (const file of FILES) {
  const source = fs.readFileSync(dir + file + '.js', 'utf8');
  for (const [, key, german] of source.matchAll(/(?:tr|tx)\('([\w.]+)',\s*'((?:[^'\\]|\\.)*)'/g)) {
   assert.ok(key in en, `${file}: ${key} fehlt im englischen Paket`);
   assert.ok(key in de, `${file}: ${key} fehlt im deutschen Paket`);
   assert.equal(placeholders(en[key]), placeholders(german), `${file}: Platzhalter von ${key}`);
  }
 }
});

function load(file, language) {
 const pack = language === 'en' ? en : de;
 const context = vm.createContext({ LcI18n: { language: () => language, t: (key, values = {}) => String(pack[key] ?? key).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? `{${n}}`)) } });
 context.globalThis = context;
 vm.runInContext(fs.readFileSync(dir + file + '.js', 'utf8'), context);
 return context;
}

test('the submission card and the key figures read in English when English is chosen', () => {
 const corp = load('corporate-profile', 'en').LcCorporateProfile;
 const html = corp.checkHtml(corp.submissionCheck({ results: [{ severity: 'DISCREPANCY', code: 'MISSING_DOCUMENT', message: 'm', advice: 'a' }, { severity: 'DISCREPANCY', code: 'X', message: 'n' }] }), false);
 assert.match(html, /Not ready for submission/); assert.match(html, /2 points must be resolved first \(including 1 missing document\)/); assert.match(html, /How to fix: a/);
 const kpis = load('kpis', 'en').LcKpis;
 assert.match(kpis.html(kpis.kpis([], '2026-10-20')), /Open files[\s\S]*Expiry within 14 days/);
 assert.match(load('kpis', 'de').LcKpis.html(load('kpis', 'de').LcKpis.kpis([], '2026-10-20')), /Offene Akten/);
 const ds = load('document-set', 'en').LcDocumentSet;
 assert.equal(ds.limitMessage(null, null, 'EUR').text, 'Total: line amounts are still missing.');
 assert.match(load('draft-check', 'en').LcDraftCheck.resultHtml({ textFound: true, discrepancies: 0, warnings: 2, findings: [] }), /No discrepancy found<\/b> · 2 notes to check/);
});

test('findings from the server show their English wording in English and the German one otherwise', () => {
 const finding = { severity: 'DISCREPANCY', code: 'MISSING_DOCUMENT', message: 'Packliste fehlt', messageEn: 'A document required by the credit has not been presented.', advice: 'hochladen', adviceEn: 'upload it' };
 const en = load('corporate-profile', 'en').LcCorporateProfile, de = load('corporate-profile', 'de').LcCorporateProfile;
 assert.match(en.checkHtml(en.submissionCheck({ results: [finding] }), false), /has not been presented[\s\S]*How to fix: upload it/);
 assert.match(de.checkHtml(de.submissionCheck({ results: [finding] }), false), /Packliste fehlt[\s\S]*So beheben: hochladen/);
 assert.match(en.checkHtml(en.submissionCheck({ results: [{ ...finding, messageEn: null, adviceEn: null }] }), false), /Packliste fehlt[\s\S]*How to fix: hochladen/);
 const dc = load('draft-check', 'en').LcDraftCheck;
 assert.match(dc.resultHtml({ textFound: true, discrepancies: 1, warnings: 0, findings: [finding] }), /has not been presented[\s\S]*How to fix: upload it/);
});

test('the document list and the deadline monitor read in English', () => {
 const source = fs.readFileSync(dir + 'app.js', 'utf8');
 const pick = name => { const start = source.indexOf(`function ${name}(`); return source.slice(start, source.indexOf('\n', start)); };
 const context = vm.createContext({ LcI18n: { language: () => 'en', t: (key, values = {}) => String(en[key] ?? key).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? '')) }, currentUser: { role: 'VIEWER' }, can: () => false,
  esc: v => String(v ?? ''), documentRow: () => '<row>' });
 context.globalThis = context;
 vm.runInContext([pick('tx'), pick('documentCategory'), pick('renderDocuments')].join('\n'), context);
 const html = context.renderDocuments([{ documentType: 'COMMERCIAL_INVOICE' }, { documentType: 'BILL_OF_LADING' }]);
 assert.match(html, /2 documents in this LC file/); assert.match(html, /2 sections/); assert.match(html, /Commercial documents[\s\S]*Transport documents/);
 assert.match(context.renderDocuments([]), /No documents in this LC file yet/);
});

test('the procurement plan and the status names read in English', () => {
 const plan = load('procurement-plan', 'en').LcProcurementPlan;
 const html = plan.planHtml({ latestShipment: '2026-10-29', daysToShipment: 9, shipmentRisk: 'SOON', open: 2, overdue: 0, critical: 1, items: [
  { risk: 'CRITICAL', documentName: 'Certificate of Origin', requirement: 'CERTIFICATE OF ORIGIN', hint: 'Vorlauf 7 Arbeitstage vor dem spätesten Versanddatum.', hintEn: 'Lead time 7 working days before the latest shipment date.', present: false, documentType: 'CERTIFICATE_OF_ORIGIN', status: 'NOT_STARTED', dueDate: '2026-10-20', daysLeft: 0 }] });
 assert.match(html, /Procurement plan/); assert.match(html, /Latest shipment 29\/10\/2026 \(in 9 days\)/); assert.match(html, /Lead time 7 working days/); assert.doesNotMatch(html, /Vorlauf/);
 assert.match(html, /1 critical/); assert.match(html, /Still missing/); assert.match(html, /Target date/);
 const de = load('procurement-plan', 'de').LcProcurementPlan;
 assert.match(de.planHtml({ latestShipment: '2026-10-29', daysToShipment: 9, shipmentRisk: 'SOON', open: 0, overdue: 0, critical: 0, items: [] }), /Beschaffungsplan/);
 assert.match(plan.chipHtml({ shipmentRisk: 'OK', daysToShipment: 3, open: 2, nextDocument: 'Packing List', nextDue: '2026-10-25' }), /Shipment in 3 days · 2 documents missing · next: Packing List by 25\/10\/2026/);
});

test('the status names read in English and the header buttons use the language packs', () => {
 const source = fs.readFileSync(dir + 'app.js', 'utf8');
 const pick = name => { const start = source.indexOf(`function ${name}(`); return source.slice(start, source.indexOf('\n', start)); };
 for (const [language, expected] of [['en', 'Documents presented'], ['de', 'Dokumente eingereicht']]) {
  const pack = language === 'en' ? en : de;
  const context = vm.createContext({ LcI18n: { language: () => language, t: (key, values = {}) => String(pack[key] ?? key).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? '')) } });
  vm.runInContext([pick('tx'), pick('statusLabel')].join('\n'), context);
  assert.equal(context.statusLabel('DOCUMENTS_PRESENTED'), expected); assert.equal(context.statusLabel('UNKNOWN_STATUS'), 'UNKNOWN_STATUS');
 }
 assert.equal(en['lc.conditions'], 'LC conditions'); assert.equal(de['lc.charges'], 'Gebühren & Provisionen');
});

test('the draft states, the charge types and the condition names exist in both languages', () => {
 for (const key of ['dr.draft', 'dr.submitted', 'dr.reviewed', 'dr.final', 'dr.title', 'lcx.type.OPENING', 'lcx.type.CONFIRMATION', 'lcx.cond.GOODS_DESCRIPTION', 'lcx.cond.APPLICABLE_RULES']) { assert.ok(key in en && key in de, key); }
 const source = fs.readFileSync(dir + 'app.js', 'utf8');
 const start = source.indexOf('function draftStatusLabel('), code = source.slice(start, source.indexOf('\n', start));
 const tx = source.slice(source.indexOf('function tx('), source.indexOf('\n', source.indexOf('function tx(')));
 for (const [language, pack, expected] of [['en', en, 'For review'], ['de', de, 'Zur Prüfung']]) {
  const context = vm.createContext({ LcI18n: { language: () => language, t: (key, values = {}) => String(pack[key] ?? key) } });
  vm.runInContext([tx, code].join('\n'), context);
  assert.equal(context.draftStatusLabel('SUBMITTED'), expected); assert.equal(context.draftStatusLabel('SOMETHING'), 'SOMETHING');
 }
});

test('the credit list shows deadlines and counts in English', () => {
 const source = fs.readFileSync(dir + 'app.js', 'utf8');
 const pick = name => { const start = source.indexOf(`function ${name}(`); return source.slice(start, source.indexOf('\n', start)); };
 const context = vm.createContext({ LcI18n: { language: () => 'en', t: (key, values = {}) => String(en[key] ?? key).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? '')) },
  fmtDate: v => v, daysUntil: d => d == null || d === 'x' ? 99999 : Number(d) });
 vm.runInContext([pick('tx'), pick('statusLabel'), pick('deadlineInfo')].join('\n'), context);
 assert.equal(context.deadlineInfo({ status: 'ACTIVE', expiryDate: '-3', latestShipmentDate: '10', followUpDate: 'x' }).text, 'Expired 3 days ago');
 assert.equal(context.deadlineInfo({ status: 'ACTIVE', expiryDate: '5', latestShipmentDate: '10', followUpDate: 'x' }).text, 'Expires in 5 days');
 assert.equal(context.deadlineInfo({ status: 'ACTIVE', expiryDate: null, latestShipmentDate: null, followUpDate: null }).text, 'No expiry date');
 assert.equal(context.deadlineInfo({ status: 'CLOSED' }).text, 'Closed');
 for (const key of ['gf.title', 'gf.cancel', 'gf.pdf', 'ls.none', 'hm.credits', 'hm.mine']) assert.ok(key in en && key in de, key);
 const html = fs.readFileSync(dir + 'index.html', 'utf8');
 for (const [, key] of html.matchAll(/data-i18n(?:-placeholder)?="([\w.]+)"/g)) assert.ok(key in en || key.startsWith('audit.') || key.startsWith('nav.') || key.startsWith('profile.'), key + ' fehlt im englischen Paket');
});

test('the inbox item, its preview and the suggestions render in English without leftovers', () => {
 const source = fs.readFileSync(dir + 'app.js', 'utf8');
 const fn = name => { const start = source.indexOf(`function ${name}(`); const rest = source.slice(start); const lines = rest.split('\n'); let depth = 0, out = [];
  for (const line of lines) { out.push(line); for (const c of line) { if (c === '{') depth++; else if (c === '}') depth--; } if (depth === 0 && out.length > 0 && /\}\s*$/.test(line)) break; } return out.join('\n'); };
 const consts = ['const inboxDocumentTypes=', 'const documentCopyOptions'].map(prefix => { const i = source.indexOf(prefix); return i < 0 ? '' : source.slice(i, source.indexOf('\n', i)); }).join('\n');
 for (const [language, pack] of [['en', en], ['de', de]]) {
  const context = vm.createContext({ LcI18n: { language: () => language, locale: () => language === 'en' ? 'en-GB' : 'de-DE', t: (key, values = {}) => String(pack[key] ?? key).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? '')) },
   Intl, Date, Math, esc: v => String(v ?? ''), can: () => true, fileSize: n => n + ' B', extractionLabel: s => s || '', statusLabel: s => s, inboxMetadataFields: () => '', inboxNewCaseHtml: () => '', globalThis: {} });
  context.globalThis = context;
  vm.runInContext([consts, fn('tx'), fn('documentCopyLabel'), fn('documentCopyOptions'), fn('inboxPrefill'), fn('renderInboxSuggestions'), fn('renderClassificationHint'), fn('inboxAssignmentPreview'), fn('renderInboxItem')].join('\n'), context);
  const item = { id: 'i1', originalFilename: 'doc.pdf', fileSize: 10, receivedBy: 'anna', receivedAt: '2026-10-21T08:00:00', extractionStatus: 'EXTRACTED', contentType: 'application/pdf', extractedReference: 'LC-1',
   classification: { suggestedType: 'COMMERCIAL_INVOICE', status: 'SUGGESTED', score: 0.82, evidence: ['x'] }, assignmentCandidates: [{ lcId: 'l1', reference: 'LC-1', status: 'ACTIVE', reason: 'REFERENCE_EXACT', evidence: 'ref' }] };
  const html = context.renderInboxItem(item, [{ id: 'l1', reference: 'LC-1', status: 'ACTIVE' }]);
  assert.ok(!html.includes('${') && !/tx\('/.test(html), language + ': unrendered template left');
  if (language === 'en') {
   for (const text of ['Received from anna', 'View document', 'Recognised LC reference: LC-1', 'Please choose', 'Commercial invoice', 'Confirm assignment', 'Previous page', 'Next page', 'Suggested LC file', 'Recognised LC reference matches', 'Choose suggestion', 'Type recognition: Commercial invoice', 'Score 82 / 100', 'Preview for the assignment']) assert.ok(html.includes(text), text);
   assert.ok(!/Eingang|Bitte auswählen|Zuordnung bestätigen|Vorschlag auswählen/.test(html), 'German leftover');
  } else {
   for (const text of ['Eingang von anna', 'Dokument ansehen', 'Handelsrechnung', 'Zuordnung bestätigen']) assert.ok(html.includes(text), text);
  }
 }
});

test('the inbox toolbar and the inbox page texts exist in both languages', () => {
 const source = fs.readFileSync(dir + 'app.js', 'utf8');
 const start = source.indexOf('function inboxBulkToolbar('), code = source.slice(start, source.indexOf('\n', start));
 const tx = source.slice(source.indexOf('function tx('), source.indexOf('\n', source.indexOf('function tx(')));
 for (const [language, pack, expected] of [['en', en, 'Delete selected'], ['de', de, 'Ausgewählte löschen']]) {
  const context = vm.createContext({ LcI18n: { language: () => language, t: (key, values = {}) => String(pack[key] ?? key) }, can: () => true, esc: v => String(v) });
  vm.runInContext([tx, code].join('\n'), context);
  const html = context.inboxBulkToolbar();
  assert.ok(html.includes(expected), language); assert.ok(!html.includes('tx(') && !html.includes('${'), language + ' leftover');
 }
 const source2 = fs.readFileSync(dir + 'app.js', 'utf8');
 for (const key of ['inbox.title', 'inbox.intro', 'inbox.refresh', 'inbox.pick', 'inbox.limits', 'inbox.upload']) { assert.ok(source2.includes(`data-i18n="${key}"`), key); assert.ok(key in en && key in de, key); }
});

test('the recognition status and the metadata block read in English', () => {
 const source = fs.readFileSync(dir + 'app.js', 'utf8');
 const pick = name => { const start = source.indexOf(`function ${name}(`); return source.slice(start, source.indexOf('\n', start)); };
 const context = vm.createContext({ LcI18n: { language: () => 'en', t: (key, values = {}) => String(en[key] ?? key).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? '')) }, esc: v => String(v ?? '') });
 vm.runInContext([pick('tx'), pick('extractionLabel'), pick('inboxMetadataFields')].join('\n'), context);
 assert.equal(context.extractionLabel('EXTRACTED'), 'Text read automatically'); assert.equal(context.extractionLabel(undefined), 'Not processed'); assert.equal(context.extractionLabel('FUTURE'), 'FUTURE');
 const html = context.inboxMetadataFields({ id: 'i', extractedReference: 'LC-1', extractedAmount: null });
 assert.ok(!html.includes('${') && !html.includes('tx('), 'leftover');
 for (const text of ['Check and correct metadata', 'LC reference in the document', 'Recognised – check', 'Not recognised / uncertain', 'Save working state', 'Confirm layout & learn', 'Show evidence']) assert.ok(html.includes(text), text);
 assert.ok(!/prüfen|Bearbeitungsstand|Fundstellen/.test(html), 'German leftover');
});

test('audit events are labelled in both languages, including actions without an old label', () => {
 for (const code of ['LOGIN', 'DOCUMENT_UPLOADED', 'DOCUMENT_SET_GENERATED', 'LC_COPIED', 'DRAFT_CHECKED', 'AMENDMENT_REQUEST_CREATED', 'SUBMISSION_PACKAGE_CREATED', 'TASK_CLAIMED']) assert.ok(('audit.act.' + code) in en && ('audit.act.' + code) in de, code);
 const source = fs.readFileSync(dir + 'app.js', 'utf8');
 const pick = name => { const start = source.indexOf(`function ${name}(`); return source.slice(start, source.indexOf('\n', start)); };
 for (const [language, pack, expected] of [['en', en, 'Document set created'], ['de', de, 'Dokumentensatz erzeugt']]) {
  const context = vm.createContext({ LcI18n: { language: () => language, t: (key, values = {}) => String(pack[key] ?? key) }, auditLabels: { DOCUMENT_SET_GENERATED: 'x' } });
  vm.runInContext([pick('tx'), pick('auditLabel')].join('\n'), context);
  assert.equal(context.auditLabel('DOCUMENT_SET_GENERATED'), expected); assert.equal(context.auditLabel('SOMETHING_UNKNOWN'), 'SOMETHING_UNKNOWN');
 }
 const keys = [...fs.readFileSync(dir + 'app.js', 'utf8').matchAll(/tx\('([\w.]+)'/g)].map(m => m[1]);
 const missing = [...new Set(keys)].filter(key => !key.endsWith('.') && (!(key in en) || !(key in de))); // keys that end with a dot are completed at run time
 assert.deepEqual(missing, [], 'Schlüssel ohne Übersetzung in app.js');
});

test('the document buttons are grouped into two menus with translated labels', () => {
 for (const [language, pack, create, check] of [['en', en, 'Create', 'Check & submit'], ['de', de, 'Erzeugen', 'Prüfen & einreichen']]) {
  const context = vm.createContext({ LcI18n: { language: () => language, t: (key, values = {}) => String(pack[key] ?? key) } });
  context.globalThis = context;
  vm.runInContext(fs.readFileSync(dir + 'action-menus.js', 'utf8'), context);
  const groups = JSON.parse(JSON.stringify(context.LcActionMenus.GROUPS()));
  assert.deepEqual(groups.map(g => g.id), ['create', 'check']); assert.deepEqual(groups.map(g => g.label), [create, check]);
  assert.deepEqual(groups[0].items, ['[data-doc-create]', '#documentSetBtn']); assert.deepEqual(groups[1].items, ['#draftCheckBtn', '#submissionPackageBtn', '#amendmentRequestBtn']);
 }
});

test('global search finds files by reference and parties, best reference match first', () => {
  const context = vm.createContext({ globalThis: null }); context.globalThis = context;
  vm.runInContext(fs.readFileSync(dir + 'global-search.js', 'utf8'), context);
  const list = [{ reference: 'LC-2', beneficiary: 'Acme GmbH' }, { reference: 'ACME-1', applicant: 'Foo' }, { reference: 'LC-3', beneficiary: 'Other' }];
  assert.deepEqual(JSON.parse(JSON.stringify(context.LcSearch.search(list, 'acme').map(x => x.reference))), ['ACME-1', 'LC-2']);
  assert.equal(context.LcSearch.search(list, '').length, 0);
});
