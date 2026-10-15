const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({}); context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/evidence-text.js', 'utf8'), context);
test('rule pack evidence keeps only recorded facts', () => {
 const text = 'DOCUMENT_AMOUNT = 1000 · LESS_OR_EQUAL · SHA-256 ab · Ergebnis PASS · Prüfdaten {LC_CONSIGNEE=null, DOCUMENT_AMOUNT=1000, LC_AMOUNT=1200, DOCUMENT_APPLICANT_ADDRESS=BUYER LLC, DUBAI, UAE, LC_DRAWEE=null} · Partner';
 assert.equal(context.readableEvidence(text), 'DOCUMENT_AMOUNT = 1000 · LESS_OR_EQUAL · SHA-256 ab · Ergebnis PASS · Erfasste Prüfdaten: DOCUMENT_AMOUNT=1000; LC_AMOUNT=1200; DOCUMENT_APPLICANT_ADDRESS=BUYER LLC, DUBAI, UAE · Partner');
 assert.match(context.readableEvidence('X · Prüfdaten {A_B=null, C_D=null}'), /keine weiteren Prüfdaten erfasst/);
 assert.equal(context.readableEvidence('ohne Daten'), 'ohne Daten');
 assert.equal(context.readableEvidence(null), '');
});
