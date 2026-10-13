const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/procurement-plan.js', 'utf8');
const context = vm.createContext({ globalThis: {} });
context.globalThis = context;
vm.runInContext(source, context);
const api = context.LcProcurementPlan;
const plan = { latestShipment: '2026-11-20', daysToShipment: 8, shipmentRisk: 'SOON', open: 1, overdue: 1, critical: 0,
 items: [{ requirement: 'Certificate of origin <b>', documentName: 'Certificate of Origin', present: false, dueDate: '2026-11-11', daysLeft: -2, risk: 'OVERDUE', hint: 'Richtwert 7 Arbeitstage' },
  { requirement: 'Packing list', documentName: 'Packing List', present: true, dueDate: '2026-11-17', daysLeft: 5, risk: 'DONE', hint: '' }] };
test('plan shows what is there, what is missing and the traffic light, escaping clause text', () => {
 const html = api.planHtml(plan);
 assert.match(html, /Überfällig/); assert.match(html, /Fehlt noch/); assert.match(html, /Hochgeladen/); assert.match(html, /1 vorhanden/); assert.match(html, /1 überfällig/);
 assert.doesNotMatch(html, /<b>"?>/); assert.match(html, /&lt;b&gt;/);
});
test('headline explains the shipment deadline and handles a missing date', () => {
 assert.match(api.headline(plan), /Spätester Versand 20\.11\.2026/);
 assert.match(api.headline({ latestShipment: null }), /nicht berechnet/);
 assert.match(api.headline({ ...plan, shipmentRisk: 'DONE' }), /Transportdokument liegt vor/);
});
