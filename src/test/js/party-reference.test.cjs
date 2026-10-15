const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({}); context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/party-reference.js', 'utf8'), context);
const api = context.LcPartyReference;
test('references in applicant or beneficiary are found in usual spellings', () => {
 assert.equal(api.referenceOf('SEE 47A'), '47A');
 assert.equal(api.referenceOf('AS PER FIELD 46A'), '46A');
 assert.equal(api.referenceOf('siehe Feld 47A'), '47A');
 assert.equal(api.referenceOf('SEE 47', ['47A', '46A']), '47A');
 assert.equal(api.referenceOf('MUSTER GMBH\nSTUTTGART'), null);
});
test('corrections prefill a resolved address and leave unresolved ones empty', () => {
 const preview = { applicant: 'BUYER LLC\nDUBAI', beneficiary: 'SEE 46A', rawFields: [
  { code: '50', value: 'SEE 47A' }, { code: '59', value: 'SEE 46A' }, { code: '47A', value: 'APPLICANT: BUYER LLC\nDUBAI' }, { code: '46A', value: '1.INVOICE' } ] };
 const list = Array.from(api.corrections(preview));
 assert.equal(list.length, 2);
 assert.equal(list[0].key, 'applicant'); assert.equal(list[0].suggested, 'BUYER LLC\nDUBAI'); assert.equal(list[0].source, '47A');
 assert.equal(list[1].key, 'beneficiary'); assert.equal(list[1].suggested, ''); assert.equal(list[1].sourceText, '1.INVOICE');
 assert.match(api.html(list), /data-party="beneficiary"/);
 assert.equal(api.corrections({ applicant: 'A', beneficiary: 'B', rawFields: [{ code: '50', value: 'A' }, { code: '59', value: 'B' }] }).length, 0);
});
