const test = require('node:test'), assert = require('node:assert');
const vm = require('node:vm'), fs = require('node:fs'), path = require('node:path');
const load = file => { const ctx = { globalThis: {} }; ctx.globalThis = ctx; vm.createContext(ctx); vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static', file), 'utf8'), ctx); return ctx; };
const missingRule = doc => ({ code: 'PACK_X', severity: 'WARNING', message: 'Regel nicht prüfbar: Dokumentdatum nach Vorlagedatum', lcCondition: 'UCP 600 Art. 14(i) · LC_PRESENTATION_DATE = nicht erfasst', documentName: doc });
test('rules waiting for missing LC data are collected once and left out of the documents', () => {
 const r = load('document-review.js').LcDocumentReview;
 const results = [missingRule('a.pdf'), missingRule('b.pdf'), { code: 'AMOUNT', severity: 'DISCREPANCY', message: 'Betrag', documentName: 'a.pdf' }];
 const missing = r.missingInputs(results);
 assert.strictEqual(missing.length, 1); assert.strictEqual(missing[0].field, 'LC_PRESENTATION_DATE'); assert.strictEqual(missing[0].rules, 2); assert.strictEqual(missing[0].documents.size, 2);
 const groups = r.groupFindings(results, []);
 assert.strictEqual(groups.map(g => g.name).join(), 'a.pdf'); assert.strictEqual(groups[0].items.length, 1);
 assert.strictEqual(r.missingField({ ...missingRule('a.pdf'), reviewDecision: 'ACCEPTED' }), null);
 assert.strictEqual(r.missingField({ ...missingRule('a.pdf'), message: 'Regel verletzt: x' }), null);
});
test('presentation date is saved only for the presented status and never in the future', () => {
 const p = load('presentation-date.js').LcPresentationDate;
 assert.strictEqual(p.dateToSave('DOCUMENTS_PRESENTED', '2026-10-10', '2026-10-16'), '2026-10-10');
 assert.strictEqual(p.dateToSave('DOCUMENTS_PRESENTED', '2026-10-20', '2026-10-16'), null);
 assert.strictEqual(p.dateToSave('ACTIVE', '2026-10-10', '2026-10-16'), null);
 assert.strictEqual(p.dateToSave('DOCUMENTS_PRESENTED', '', '2026-10-16'), null);
});
