const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/kpis.js', 'utf8'), context);
const api = context.LcKpis, plain = v => JSON.parse(JSON.stringify(v));
const list = [
 { status: 'ACTIVE', amount: 1000, currency: 'EUR', expiryDate: '2026-10-25', latestShipmentDate: '2026-10-21', assignedTo: 'anna' },
 { status: 'RECEIVED', amount: 500, currency: 'EUR', expiryDate: '2026-12-31', followUpDate: '2026-10-19' },
 { status: 'WAITING_FOR_CUSTOMER', amount: 200, currency: 'USD', expiryDate: '2026-10-10', assignedTo: 'bob' },
 { status: 'CLOSED', amount: 9999, currency: 'EUR', expiryDate: '2026-10-22' },
 { status: 'EXPIRED', amount: 1, currency: 'USD' }];

test('figures count open files only and sum the volume per currency', () => {
 const k = plain(api.kpis(list, '2026-10-20'));
 assert.equal(k.open, 3); assert.equal(k.closed, 2);
 assert.deepStrictEqual(k.volume, { EUR: 1500, USD: 200 });
 assert.equal(k.expiring, 1); assert.equal(k.shipping, 1); assert.equal(k.overdueExpiry, 1);
 assert.equal(k.followUps, 1); assert.equal(k.unassigned, 1); assert.equal(k.waiting, 1);
});

test('the tiles are escaped and highlight what needs attention', () => {
 const html = api.html(api.kpis([{ status: 'ACTIVE', amount: 5, currency: '<b>', expiryDate: '2026-10-21' }], '2026-10-20'));
 assert.ok(!html.includes('5 <b>')); assert.match(html, /&lt;b&gt;/); assert.match(html, /kpi kpi-hot/);
 assert.match(api.html(api.kpis([], '2026-10-20')), /Volumen offen/);
});
