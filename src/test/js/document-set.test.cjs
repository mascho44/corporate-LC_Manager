const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/document-set.js', 'utf8'), context);
const api = context.LcDocumentSet;

test('a line amount is the entered amount or quantity times unit price', () => {
  assert.equal(api.lineAmount({ amount: '1.250,5' }), null);
  assert.equal(api.lineAmount({ amount: '1250.5' }), 1250.5);
  assert.equal(api.lineAmount({ quantity: '2', unitPrice: '58000' }), 116000);
  assert.equal(api.lineAmount({ quantity: '3', unitPrice: '0,333' }), 1);
  assert.equal(api.lineAmount({ quantity: '2' }), null);
  assert.equal(api.lineAmount({ amount: '9', quantity: '2', unitPrice: '5' }), 9);
});

test('the total needs an amount on every line', () => {
  assert.equal(api.totalOf([{ quantity: '2', unitPrice: '58000' }, { amount: '9000' }]), 125000);
  assert.equal(api.totalOf([{ amount: '1' }, { quantity: '1' }]), null);
  assert.equal(api.totalOf([]), 0);
});

test('the limit message says how far the total is over or under the maximum', () => {
  assert.equal(api.limitMessage(null, 131250, 'EUR').level, 'hint');
  const over = api.limitMessage(131500, 131250, 'EUR');
  assert.equal(over.level, 'error'); assert.match(over.text, /250,00 EUR über dem zulässigen Höchstbetrag 131\.250,00 EUR/);
  const under = api.limitMessage(125000, 131250, 'EUR');
  assert.equal(under.level, 'ok'); assert.match(under.text, /zulässig bis 131\.250,00 EUR/);
  assert.equal(api.limitMessage(100, null, 'EUR').level, 'ok');
  assert.equal(api.limitMessage(131250, 131250, 'EUR').level, 'ok');
});

test('the request keeps only filled lines, numbers positions and converts decimals', () => {
  const body = api.requestBody({ documentDate: '2026-10-05', invoiceNumber: ' INV-1 ', notes: '  ', types: ['COMMERCIAL_INVOICE'], packages: '3', netWeight: '2544,5', grossWeight: '',
    lines: [{ description: 'Machine', quantity: '2', unit: 'pcs', unitPrice: '58000,50', amount: '', packages: '2', netWeight: '', grossWeight: '' }, { description: '', quantity: '', unit: '', unitPrice: '', amount: '' }] });
  assert.equal(body.invoiceNumber, 'INV-1'); assert.equal(body.notes, null); assert.equal(body.packages, 3); assert.equal(body.netWeight, 2544.5); assert.equal(body.grossWeight, null);
  assert.equal(body.items.length, 1);
  assert.deepEqual(JSON.parse(JSON.stringify(body.items[0])), { position: '1', description: 'Machine', quantity: 2, unit: 'pcs', unitPrice: 58000.5, amount: null, packages: 2, netWeight: null, grossWeight: null });
});

test('the result lists documents, notes and findings, escaped', () => {
  const html = api.resultHtml({ documents: [{ originalFilename: 'inv <1>.docx', downloadUrl: '/d/1', typeLabel: 'Commercial Invoice' }], total: 125000, currency: 'EUR',
    warnings: ['Summe <zu hoch>'], findings: [{ severity: 'DISCREPANCY', message: 'Betrag zu hoch', documentName: 'inv <1>.docx' }] });
  assert.match(html, /1 Dokumente erzeugt/); assert.match(html, /inv &lt;1&gt;\.docx/); assert.match(html, /125\.000,00 EUR/); assert.match(html, /Summe &lt;zu hoch&gt;/); assert.match(html, /Abweichung/);
  assert.match(api.resultHtml({ documents: [], warnings: [], findings: [] }), /nichts Auffälliges/);
});
