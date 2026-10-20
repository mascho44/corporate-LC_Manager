const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/amendment-request.js', 'utf8'), context);
const api = context.LcAmendmentRequest;

test('empty fields are left out and amounts accept German and English notation', () => {
 const b = JSON.parse(JSON.stringify(api.requestBody({ date: '2026-10-20', recipient: ' Bank ', newAmount: '130.000,50', goods: '  ' })));
 assert.equal(b.recipient, 'Bank'); assert.equal(b.newAmount, 130000.5); assert.equal(b.goods, null); assert.equal(b.newExpiryDate, null);
 assert.equal(api.requestBody({ newAmount: '130,000.50' }).newAmount, 130000.5);
 assert.equal(api.requestBody({ newAmount: '1000' }).newAmount, 1000);
});

test('a request needs at least one change', () => {
 assert.equal(api.hasChange(api.requestBody({ recipient: 'Bank' })), false);
 assert.equal(api.hasChange(api.requestBody({ recipient: 'Bank', newExpiryDate: '2026-12-31' })), true);
});
