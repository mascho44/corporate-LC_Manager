const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/avis-case.js', 'utf8'), context);
const api = context.LcAvisCase;
test('preview shows the credit data, required documents, warnings and errors, escaped', () => {
 const html = api.previewHtml({ creditTextFound: true, credit: { reference: 'LC-1 <b>', applicant: 'Buyer', beneficiary: 'Seller', amount: 125000, currency: 'EUR', expiryDate: '2026-11-30', expiryPlace: 'HAMBURG',
  requiredDocuments: ['Invoice', 'Full set B/L'], errors: ['Ein Akkreditiv mit dieser Referenz existiert bereits.'], warnings: ['Bitte prüfen'] } });
 assert.match(html, /LC-1 &lt;b&gt;/); assert.match(html, /125\.000,00 EUR/); assert.match(html, /30\.11\.2026/); assert.match(html, /Geforderte Dokumente \(2\)/); assert.match(html, /avis-error/); assert.match(html, /Bitte prüfen/);
});
test('without credit text the user is told what an advice must contain', () => {
 assert.match(api.previewHtml({ creditTextFound: false, credit: null }), /kein Akkreditivtext erkannt/);
});
test('advice without credit text offers the cover letter data as an editable form and a clean request', () => {
 const html = api.coverFormHtml({ fields: { reference: 'ABC <1>', amount: '1000.00', currency: 'EUR', expiryDate: '2026-11-30', applicant: 'Buyer' }, warnings: ['Bitte prüfen'] });
 assert.match(html, /value="ABC &lt;1&gt;"/); assert.match(html, /name="expiryDate" value="2026-11-30" type="date"/); assert.match(html, /Bitte prüfen/); assert.match(html, /Anschreiben/);
 assert.equal(api.coverFormHtml({ fields: {} }), '');
 const body = api.coverRequest({ reference: ' ABC1 ', amount: '1.000,50'.replace('.', ''), currency: 'EUR', expiryDate: '2026-11-30', beneficiary: '' });
 assert.equal(body.reference, 'ABC1'); assert.equal(body.amount, 1000.5); assert.equal(body.beneficiary, null); assert.equal(body.documentType, 'ADVISING_LETTER'); assert.equal(body.expiryDate, '2026-11-30');
});

test('annex choice: empty without partners, listed with reason, selection and escaping', () => {
  assert.equal(api.annexHtml([], null), '');
  assert.equal(api.annexHtml(undefined, null), '');
  const partners = [{ id: 'a-1', filename: 'anlage <1>.pdf', reason: 'kam mit derselben E-Mail' }, { id: 'a-2', filename: 'zweite.pdf', reason: 'enthält einen Akkreditivtext' }];
  const html = api.annexHtml(partners, 'a-2');
  assert.match(html, /<select data-annex>/);
  assert.match(html, /keine – nur dieses Dokument/);
  assert.match(html, /anlage &lt;1&gt;\.pdf · kam mit derselben E-Mail/);
  assert.match(html, /<option value="a-2" selected>/);
  assert.doesNotMatch(html, /<option value="a-1" selected>/);
  assert.match(html, /Der Akkreditivtext wird aus der Anlage gelesen/);
  assert.doesNotMatch(api.annexHtml(partners, null), /Der Akkreditivtext wird aus der Anlage gelesen/);
});
