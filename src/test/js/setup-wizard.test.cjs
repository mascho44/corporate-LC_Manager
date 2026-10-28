const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/setup-wizard.js'), 'utf8');
const sandbox = { globalThis: {} }; sandbox.globalThis = sandbox; vm.createContext(sandbox); vm.runInContext(source, sandbox);
const { plan } = sandbox.LcSetupWizard;
const keys = (bank, corporate) => plan(bank, corporate).map(step => step.key);
const names = (steps, key) => steps.find(step => step.key === key).fields.map(f => f.name);

test('a corporate sets up tax data, bank account and logo but no bank-only steps', () => {
  const steps = plan(false, true);
  assert.equal(JSON.stringify(keys(false, true)), JSON.stringify(['identity', 'contact', 'bankdata', 'logo', 'next']));
  assert.ok(names(steps, 'contact').includes('taxId'));
  assert.ok(names(steps, 'bankdata').includes('iban'));
  assert.equal(JSON.stringify(steps.at(-1).links.map(l => l.target)), JSON.stringify(['#appNavUsers', '#appNavTemplates']));
});
test('a bank sets up its institute with a BIC and the SLA and correspondent follow-ups', () => {
  const steps = plan(true, false);
  assert.equal(JSON.stringify(keys(true, false)), JSON.stringify(['identity', 'contact', 'logo', 'next']));
  assert.ok(names(steps, 'identity').includes('bic'));
  assert.ok(!names(steps, 'contact').includes('taxId'));
  assert.equal(JSON.stringify(steps.at(-1).links.map(l => l.target)), JSON.stringify(['#appNavUsers', '#appNavSla', '#appNavCorrespondent']));
});
test('a combined workspace has both, and the BIC is asked only once', () => {
  const steps = plan(true, true);
  assert.equal(JSON.stringify(keys(true, true)), JSON.stringify(['identity', 'contact', 'bankdata', 'logo', 'next']));
  const all = steps.flatMap(step => step.fields || []).map(f => f.name);
  assert.equal(all.filter(n => n === 'bic').length, 1);
});
test('the company name is required in the first step', () => {
  assert.equal(plan(false, true)[0].fields.find(f => f.name === 'legalName').required, true);
});
