const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const load = name => { const sandbox = { globalThis: {} }; sandbox.globalThis = sandbox; vm.createContext(sandbox); vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/' + name), 'utf8'), sandbox); return sandbox; };
const { describe } = load('review-closure.js').LcReviewClosure, { normalize } = load('review-close-policy.js').LcReviewClosePolicy;
const pending = { completed: false, required: 3, signers: ['anna'], startedBy: 'anna' };

test('without a closure the control offers to close', () => assert.equal(describe(null, 'anna').kind, 'close'));
test('the starter of an open closure may withdraw but not sign a second time', () => {
  const view = describe(pending, 'Anna');
  assert.equal(view.kind, 'wait'); assert.equal(view.canSign, false); assert.equal(view.canWithdraw, true); assert.equal(view.missing, 2);
});
test('another person may sign off but not withdraw', () => {
  const view = describe(pending, 'bernd');
  assert.equal(view.canSign, true); assert.equal(view.canWithdraw, false);
});
test('a completed closure is shown as done with its signers', () => {
  const view = describe({ completed: true, required: 2, signers: ['anna', 'bernd'], startedBy: 'anna' }, 'carl');
  assert.equal(view.kind, 'done'); assert.equal(JSON.stringify(view.signers), JSON.stringify(['anna', 'bernd']));
});
test('limits are cleaned and incomplete or invalid rows are refused', () => {
  assert.equal(JSON.stringify(normalize([{ currency: ' eur ', minimumAmount: '100000', requiredClosers: '2' }])), JSON.stringify([{ currency: 'EUR', minimumAmount: 100000, requiredClosers: 2 }]));
  assert.throws(() => normalize([{ currency: 'EU', minimumAmount: '1', requiredClosers: 2 }]));
  assert.throws(() => normalize([{ currency: 'EUR', minimumAmount: '', requiredClosers: 2 }]));
  assert.throws(() => normalize([{ currency: 'EUR', minimumAmount: '1', requiredClosers: 6 }]));
});
