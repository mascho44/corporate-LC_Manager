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
test('list chip shows shipment light, days and what is missing', () => {
 const html = api.chipHtml({ shipmentRisk: 'CRITICAL', daysToShipment: 2, open: 3, nextDocument: 'Certificate of Origin', nextDue: '2026-11-11' });
 assert.match(html, /Kritisch/); assert.match(html, /Versand in 2 Tagen/); assert.match(html, /3 Dokumente fehlen/); assert.match(html, /Certificate of Origin bis 11\.11\.2026/);
 assert.match(api.chipHtml({ shipmentRisk: 'DONE', daysToShipment: -3, open: 0 }), /versandt · komplett/);
 assert.equal(api.chipHtml(null), '');
});
test('open documents get a status selector with note when editable, plain text otherwise', () => {
 const items = [{ requirement: 'Packing list', documentType: 'PACKING_LIST', documentName: 'Packing List', present: false, dueDate: '2026-11-17', daysLeft: 3, risk: 'CRITICAL', hint: '', status: 'REQUESTED', note: 'beim Lager' }];
 const base = { latestShipment: '2026-11-20', daysToShipment: 8, shipmentRisk: 'SOON', open: 1, overdue: 0, critical: 1, items };
 const editable = api.planHtml(base, { canEdit: true, canSettings: true });
 assert.match(editable, /data-plan-status/); assert.match(editable, /option value="REQUESTED" selected/); assert.match(editable, /value="beim Lager"/); assert.match(editable, /data-plan-lead-times/);
 const readOnly = api.planHtml(base, {});
 assert.doesNotMatch(readOnly, /data-plan-status/); assert.match(readOnly, /Beantragt \/ bestellt · beim Lager/); assert.doesNotMatch(readOnly, /data-plan-lead-times/);
});
