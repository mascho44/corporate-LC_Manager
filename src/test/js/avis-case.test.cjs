const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
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
