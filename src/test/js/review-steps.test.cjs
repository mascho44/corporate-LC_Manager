const test = require('node:test'), assert = require('node:assert');
const vm = require('node:vm'), fs = require('node:fs'), path = require('node:path');
const ctx = {}; ctx.globalThis = ctx; vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/review-steps.js'), 'utf8'), ctx);
const S = ctx.LcReviewSteps;
const plain = x => JSON.parse(JSON.stringify(x));
const f = (code, extra = {}) => ({ code, severity: 'WARNING', message: 'x', documentName: 'a.pdf', ...extra });
test('the seven steps are fixed and in the professional order', () => {
 assert.strictEqual(S.STEPS.map(s => s.key).join(), 'AKTE,VOLLSTAENDIGKEIT,ANGABEN,COMPLIANCE,DOKUMENTE,QUERPRUEFUNG,ABSCHLUSS');
});
test('findings are assigned to the first matching step', () => {
 assert.strictEqual(S.stepOf(f('SANCTIONS_HIT')), 'COMPLIANCE');
 assert.strictEqual(S.stepOf(f('HS_CODE_DUAL_USE')), 'COMPLIANCE');
 assert.strictEqual(S.stepOf(f('MISSING_DOCUMENT_BILL_OF_LADING', { documentName: '' })), 'VOLLSTAENDIGKEIT');
 assert.strictEqual(S.stepOf(f('DOCUMENT_ORIGINAL_COUNT')), 'VOLLSTAENDIGKEIT');
 assert.strictEqual(S.stepOf(f('FULL_SET_BILL_OF_LADING_REVIEW')), 'VOLLSTAENDIGKEIT');
 assert.strictEqual(S.stepOf(f('PACK_X', { message: 'Regel nicht prüfbar: Dokumentdatum', lcCondition: 'UCP · LC_PRESENTATION_DATE = nicht erfasst' })), 'ANGABEN');
 assert.strictEqual(S.stepOf(f('INVOICE_PACKING_QUANTITY_CONSISTENCY')), 'QUERPRUEFUNG');
 assert.strictEqual(S.stepOf(f('INVOICE_AMOUNT_EXCEEDED')), 'QUERPRUEFUNG');
 assert.strictEqual(S.stepOf(f('ADDITIONAL_CONDITIONS_REVIEW', { documentName: '' })), 'QUERPRUEFUNG');
 assert.strictEqual(S.stepOf(f('SIGNATURE_REQUIREMENT_REVIEW')), 'DOKUMENTE');
 assert.strictEqual(S.stepOf(f('ORIGIN_CERTIFICATE_GOODS_REVIEW')), 'DOKUMENTE');
 assert.strictEqual(S.stepOf({}), 'QUERPRUEFUNG');
});
test('only open findings count, blocking ones are named, decided ones are counted separately', () => {
 const sum = S.summarize([f('SANCTIONS_HIT'), f('SANCTIONS_X', { reviewDecision: 'ACCEPTED' }), f('SIGNATURE_REQUIREMENT_REVIEW'), f('DOCUMENT_PRESENT', { severity: 'OK' }), f('REVIEW_STALE'), f('INVOICE_AMOUNT_EXCEEDED', { severity: 'DISCREPANCY' })]);
 assert.deepStrictEqual(plain([sum.COMPLIANCE.open, sum.COMPLIANCE.blocking, sum.COMPLIANCE.decided]), [1, 1, 1]);
 assert.deepStrictEqual(plain([sum.DOKUMENTE.open, sum.DOKUMENTE.blocking]), [1, 1]);
 assert.deepStrictEqual(plain([sum.QUERPRUEFUNG.open, sum.QUERPRUEFUNG.blocking]), [1, 1]);
 assert.strictEqual(sum.VOLLSTAENDIGKEIT.open, 0); assert.strictEqual(sum.AKTE.open, 0);
 assert.strictEqual(S.canComplete(sum.COMPLIANCE), false); assert.strictEqual(S.canComplete(sum.VOLLSTAENDIGKEIT), true); assert.strictEqual(S.canComplete(undefined), true);
});
test('progress and the current step follow the recorded steps', () => {
 assert.strictEqual(S.currentIndex([]), 0);
 assert.strictEqual(S.currentIndex([{ step: 'AKTE', status: 'DONE' }, { step: 'VOLLSTAENDIGKEIT', status: 'SKIPPED' }]), 2);
 const all = S.STEPS.map(s => ({ step: s.key, status: 'DONE' }));
 assert.strictEqual(S.currentIndex(all), 6);
 assert.deepStrictEqual(plain(S.progress([{ step: 'AKTE', status: 'DONE' }, { step: 'ANGABEN', status: 'SKIPPED' }])), { done: 1, skipped: 1, total: 7, finished: false });
 assert.strictEqual(S.progress(all).finished, true);
});
test('days until a date', () => {
 const now = new Date(2026, 9, 17, 15, 0);
 assert.strictEqual(S.daysUntil('2026-10-20', now), 3); assert.strictEqual(S.daysUntil('2026-10-17', now), 0); assert.strictEqual(S.daysUntil('2026-10-10', now), -7);
 assert.strictEqual(S.daysUntil(null, now), null); assert.strictEqual(S.daysUntil('kein datum', now), null);
});
