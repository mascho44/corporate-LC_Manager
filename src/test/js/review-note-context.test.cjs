const test = require('node:test'), assert = require('node:assert');
const vm = require('node:vm'), fs = require('node:fs'), path = require('node:path');
const ctx = {}; ctx.globalThis = ctx; vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/review-note.js'), 'utf8'), ctx);
const n = ctx.LcReviewNote;
test('reasons fitting the finding come after own templates and before the general ones', () => {
 const list = n.templatesFor('ACCEPTED', ['Eigene Vorlage'], { code: 'BENEFICIARY_ADDRESS_MISMATCH' });
 assert.strictEqual(list[0], 'Eigene Vorlage');
 assert.match(list[1], /Art\. 14\(j\)/);
 assert.ok(list.includes('Original geprüft, Angabe im Dokument vorhanden.'));
});
test('a finding without a known kind gets only the general reasons', () => {
 assert.strictEqual(n.contextTemplates('ACCEPTED', { code: 'XYZ' }).length, 0);
 assert.match(n.contextTemplates('CONFIRMED_DISCREPANCY', { code: 'INVOICE_AMOUNT_EXCEEDED' })[0], /Art\. 30/);
 assert.match(n.contextTemplates('ACCEPTED', { code: 'PACK_1', message: 'Regel verletzt: On board date nach Latest Shipment' })[0], /Verladedatum/);
});
