const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/first-steps.js'), 'utf8');
const sandbox = { globalThis: {} }; sandbox.globalThis = sandbox; vm.createContext(sandbox); vm.runInContext(source, sandbox);
const { steps } = sandbox.LcFirstSteps;
const empty = { companies: 0, templates: 0, activeMembers: 1, slaRules: 0, correspondentBanks: 0 };

test('an empty tenant has every step open', () => {
  assert.ok(steps(empty, 0, true).every(step => !step.done));
});
test('bank steps are marked and follow the counts', () => {
  const bank = steps({ ...empty, slaRules: 2, correspondentBanks: 0 }, 0, true).filter(step => step.bank);
  assert.equal(JSON.stringify(bank.map(step => [step.key, step.done])), JSON.stringify([['sla', true], ['correspondent', false]]));
});
test('company, templates, users and file steps are not bank steps', () => {
  const others = steps({ companies: 1, templates: 1, activeMembers: 3, slaRules: 0, correspondentBanks: 0 }, 5).filter(step => !step.bank);
  assert.equal(JSON.stringify(others.map(step => [step.key, step.done])), JSON.stringify([['company', true], ['templates', true], ['users', true], ['file', true]]));
});
test('without the bank flag only the general steps are listed', () => {
  assert.equal(steps(empty, 0).length, 4);
  assert.equal(steps(empty, 0, true).length, 6);
});
