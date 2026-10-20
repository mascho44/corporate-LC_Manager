const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const dir = 'src/main/resources/static/';
const FILES = ['app', 'corporate-profile', 'kpis', 'akte-copy', 'draft-check', 'amendment-request', 'document-set', 'submission-package'];
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
