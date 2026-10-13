const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/customers.js', 'utf8'), context);
const api = context.LcCustomers;
const customers = [{ id: 'c1', name: 'Beispiel <Export> GmbH', customerNumber: '4711' }, { id: 'c2', name: 'Andere AG' }];
test('clear suggestion offers confirmation, escapes names and lists what the letter says', () => {
 const html = api.blockHtml({ fields: { customerName: 'Beispiel Export GmbH', iban: 'DE89370400440532013000' }, candidates: [{ customerId: 'c1', name: 'Beispiel <Export> GmbH', reason: 'IBAN' }], clear: true, assigned: null }, customers);
 assert.match(html, /<dt>IBAN<\/dt><dd>DE89370400440532013000<\/dd>/); assert.match(html, /data-customer-confirm="c1"/); assert.match(html, /Beispiel &lt;Export&gt; GmbH/); assert.match(html, /data-customer-select/);
 assert.doesNotMatch(html, /Zuordnung entfernen/);
});
test('unclear or missing data asks for a manual choice; an assigned customer can be removed', () => {
 assert.match(api.blockHtml({ fields: {}, candidates: [], clear: false, assigned: null }, customers), /Kein Kunde erkannt\. Bitte den Kunden auswählen/);
 assert.match(api.blockHtml({ fields: {}, candidates: [], clear: false, assigned: null }, customers), /keine Kundenangaben erkannt/);
 const two = api.blockHtml({ fields: { customerName: 'X' }, candidates: [{ customerId: 'c1', name: 'A', reason: 'Name' }, { customerId: 'c2', name: 'B', reason: 'Name' }], clear: false, assigned: null }, customers);
 assert.match(two, /Mögliche Kunden/); assert.doesNotMatch(two, /data-customer-confirm/);
 const done = api.blockHtml({ fields: {}, candidates: [], clear: false, assigned: { customerId: 'c2', name: 'Andere AG', method: 'MANUAL' } }, customers);
 assert.match(done, /Kunde: <b>Andere AG<\/b> \(manuell gewählt\)/); assert.match(done, /option value="c2" selected/); assert.match(done, /Zuordnung entfernen/);
 assert.match(api.blockHtml({ fields: {}, candidates: [], clear: false, assigned: null }, []), /noch kein Kunde angelegt/);
});
test('the letter facts of a bank form are listed, an unverified IBAN is marked as such', () => {
 const html = api.blockHtml({ fields: { lcReference: 'LCB1', yourReference: 'BW1', applicant: 'EXAMPLE VISION LTD', documentValue: 'EUR 60.229,56', ibanCandidate: 'DE893704004405320130' }, candidates: [], clear: false, assigned: null }, customers);
 assert.match(html, /<dt>Akkreditivnummer<\/dt><dd>LCB1<\/dd>/); assert.match(html, /<dt>Dokumentengegenwert<\/dt><dd>EUR 60\.229,56<\/dd>/);
 assert.match(html, /<dt>Auftraggeber \(Akkreditiv\)<\/dt><dd>EXAMPLE VISION LTD<\/dd>/); assert.match(html, /IBAN \(nicht prüfbar/);
 assert.doesNotMatch(html, /<dt>Kunde<\/dt>/);
});
