const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/saved-views.js'), 'utf8');
const sandbox = {}; sandbox.globalThis = sandbox; vm.createContext(sandbox); vm.runInContext(source, sandbox);
const { capture, restore } = sandbox.LcSavedViews;
const plain = value => JSON.parse(JSON.stringify(value));

test('capturing keeps only what differs from the neutral state', () => {
  const state = { '#search': 'acme', '#lcTrafficFilter': 'RED', '#lcSlaFilter': '', '#lcOwnerFilter': 'mine', '#lcSort': 'priority', quick: 'expiring' };
  assert.deepEqual(plain(capture(selector => state[selector])), { search: 'acme', traffic: 'RED', owner: 'mine', quick: 'expiring' });
});
test('a missing control (hidden SLA filter, no company filter) is simply left out', () => {
  assert.deepEqual(plain(capture(() => undefined)), {});
});
test('restoring sets every control, neutral where the view says nothing', () => {
  const written = {};
  restore({ owner: 'mine', sort: 'expiry' }, (selector, value) => { written[selector] = value; });
  assert.deepEqual(plain(written), { '#search': '', '#lcTrafficFilter': '', '#lcSlaFilter': '', '#lcOwnerFilter': 'mine', '#lcCompanyFilter': '', '#lcSort': 'expiry', quick: null });
});
