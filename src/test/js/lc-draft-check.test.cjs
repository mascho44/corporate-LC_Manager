const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const sandbox = { globalThis: {} }; sandbox.globalThis = sandbox; vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/lc-draft-check.js'), 'utf8'), sandbox);
const { summary } = sandbox.LcDraftReview;
test('discrepancies make the summary bad and name the count', () => {
  const s = summary({ discrepancies: 2, warnings: 3 }); assert.equal(s.level, 'bad'); assert.match(s.text, /2/);
});
test('only warnings give a warning, nothing gives the all-clear', () => {
  assert.equal(summary({ discrepancies: 0, warnings: 1 }).level, 'warn');
  assert.equal(summary({ discrepancies: 0, warnings: 0 }).level, 'ok');
});
