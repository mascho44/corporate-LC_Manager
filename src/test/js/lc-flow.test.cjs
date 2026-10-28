const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const sandbox = { globalThis: {} }; sandbox.globalThis = sandbox; vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/lc-flow.js'), 'utf8'), sandbox);
const { steps } = sandbox.LcFlow;
const states = flow => steps(flow).map(s => s.state).join(',');
const clean = { discrepancies: 0, warnings: 0, findings: [] };

test('without a draft the draft and comparison steps are skipped and the order is fixed', () => {
  const list = steps({ draft: null, differences: null, creditCheck: clean, reviewCompleted: false });
  assert.equal(list.map(s => s.key).join(','), 'draft,compare,check,documents');
  assert.equal(states({ draft: null, differences: null, creditCheck: clean, reviewCompleted: false }), 'skipped,skipped,done,later');
});
test('differences between draft and credit need attention', () => {
  const flow = { draft: { filename: 'e.txt', discrepancies: 0, warnings: 1 }, differences: [{ tag: '32B' }], creditCheck: clean, reviewCompleted: false };
  assert.equal(states(flow), 'done,attention,done,later');
  assert.match(steps(flow)[1].text, /1/);
});
test('discrepancies in the draft or the credit need attention, and a completed examination ends the order', () => {
  const flow = { draft: { filename: null, discrepancies: 2, warnings: 0 }, differences: [], creditCheck: { discrepancies: 1, warnings: 0, findings: [] }, reviewCompleted: true };
  assert.equal(states(flow), 'attention,done,attention,done');
});
test('a file without SWIFT text leaves the credit check open', () => {
  assert.equal(states({ draft: null, differences: null, creditCheck: null, reviewCompleted: false }), 'skipped,skipped,open,later');
});
