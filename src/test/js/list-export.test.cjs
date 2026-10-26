const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/list-export.js'), 'utf8');
const sandbox = {}; sandbox.globalThis = sandbox; vm.createContext(sandbox); vm.runInContext(source, sandbox);
const { csv, cell } = sandbox.LcListExport;
const row = { reference: 'LC-1', instrumentType: 'LC', status: 'ACTIVE', dossierStatus: { status: 'RED' }, applicant: 'Müller; "GmbH"', beneficiary: 'ACME', amount: 1234.5, currency: 'EUR', expiryDate: '2026-12-31', assignedTo: 'anna' };

test('german export uses semicolons and a decimal comma', () => {
  const lines = csv([row], true).split('\r\n');
  assert.equal(lines.length, 3);
  assert.ok(lines[0].startsWith('"Referenz";"Art";"Status"'));
  assert.ok(lines[1].includes('"LC-1";"Akkreditiv";"ACTIVE";"Abweichungen";"Müller; ""GmbH""";"ACME";"1234,50";"EUR"'));
});
test('english export uses commas and a decimal point', () => {
  const line = csv([row], false).split('\r\n')[1];
  assert.ok(line.includes('"1234.50"') && line.startsWith('"LC-1","'));
});
test('cells that look like formulas are defused', () => {
  assert.equal(cell('=SUM(A1)'), '"\'=SUM(A1)"');
  assert.equal(cell('@cmd'), '"\'@cmd"');
  assert.equal(cell('+1'), '"\'+1"');
  assert.equal(cell('normal'), '"normal"');
  assert.equal(cell(null), '""');
});
test('an empty list still has the header', () => {
  assert.equal(csv([], true).split('\r\n').filter(Boolean).length, 1);
});
