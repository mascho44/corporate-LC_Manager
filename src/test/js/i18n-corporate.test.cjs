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
