const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/guarantee-cockpit.js'), 'utf8');
const context = vm.createContext({ document: undefined, globalThis: {} });
context.globalThis = context;
vm.runInContext(source, context);
const { figures } = context.LcGuaranteeCockpit;
const g = (id, over) => Object.assign({ id, reference: 'G-' + id, bank: 'Bank', currency: 'EUR', amount: 1000, open: true, expiringSoon: false, status: 'ACTIVE', decisionDate: null, expiryDate: null }, over);
test('figures count open guarantees, volumes per currency, due decisions and the next dates', () => {
 const today = new Date('2026-10-24T00:00:00Z');
 const f = figures([g(1, { amount: 5000, expiryDate: '2026-12-01', expiringSoon: true }), g(2, { currency: 'USD', amount: 700, decisionDate: '2026-11-10', expiryDate: '2027-01-10' }), g(3, { open: false, amount: 99999 }), g(4, { status: 'REQUESTED', expiryDate: '2027-06-30' })],
  [{ active: true, state: 'WARN' }, { active: true, state: 'OK' }, { active: false, state: 'WARN' }], today);
 assert.equal(f.openCount, 3); assert.equal(f.volume.EUR, 6000); assert.equal(f.volume.USD, 700);
 assert.equal(f.soon, 1); assert.equal(f.decide, 1); assert.equal(f.requested, 1); assert.equal(f.warned, 1);
 assert.deepEqual(Array.from(f.next, x => x.g.id), [2, 1, 4]); assert.equal(f.next[0].kind, 'decision'); assert.equal(f.next[1].kind, 'expiry');
});
