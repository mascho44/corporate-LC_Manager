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
 assert.ok(!html.includes('5 <b>')); assert.match(html, /&lt;b&gt;/); assert.match(html, /kpi kpi-filter kpi-hot/);
 assert.match(api.html(api.kpis([], '2026-10-20')), /Volumen offen/);
});

test('quick filters pick exactly the files a tile counts', () => {
 const today = '2026-10-20', files = list;
 const pick = key => files.filter(lc => api.matches(key, lc, today)).map(lc => lc.status + ':' + (lc.currency || '') + ':' + (lc.expiryDate || ''));
 assert.equal(pick('open').length, 3); assert.equal(pick('expiring').length, 1); assert.equal(pick('shipping').length, 1); assert.equal(pick('overdue').length, 1);
 assert.equal(pick('followUps').length, 1); assert.equal(pick('unassigned').length, 1); assert.equal(pick('waiting').length, 1);
 for (const key of ['expiring', 'shipping', 'overdue', 'followUps', 'unassigned', 'waiting', 'open']) assert.equal(files.filter(lc => api.matches(key, lc, today)).length, key === 'open' ? plain(api.kpis(files, today)).open : plain(api.kpis(files, today))[{ expiring: 'expiring', shipping: 'shipping', overdue: 'overdueExpiry', followUps: 'followUps', unassigned: 'unassigned', waiting: 'waiting' }[key]], key + ' stimmt mit der Kachel überein');
 assert.equal(api.matches(null, files[3], today), true); assert.equal(api.matches('expiring', files[3], today), false);
});

test('the attention view combines what needs action soon', () => {
 const today = '2026-10-20';
 assert.equal(api.matches('attention', { status: 'ACTIVE', expiryDate: '2026-10-25' }, today), true);
 assert.equal(api.matches('attention', { status: 'ACTIVE', expiryDate: '2026-12-31', dossierStatus: { status: 'RED' } }, today), true);
 assert.equal(api.matches('attention', { status: 'ACTIVE', expiryDate: '2026-12-31', followUpDate: '2026-10-19' }, today), true);
 assert.equal(api.matches('attention', { status: 'ACTIVE', expiryDate: '2026-12-31', dossierStatus: { status: 'GREEN' } }, today), false);
 assert.equal(api.matches('attention', { status: 'CLOSED', expiryDate: '2026-10-21' }, today), false);
});

test('the strip offers views, marks the active filter and can clear it', () => {
 const k = api.kpis(list, '2026-10-20');
 const base = api.html(k, {});
 assert.match(base, /data-kpi-view="all" aria-pressed="true"/); assert.match(base, /data-kpi="expiring" aria-pressed="false"/); assert.doesNotMatch(base, /data-kpi-reset/);
 const filtered = api.html(k, { quick: 'overdue' });
 assert.match(filtered, /data-kpi="overdue" aria-pressed="true"/); assert.match(filtered, /Gefiltert: Ablauf überschritten/); assert.match(filtered, /data-kpi-reset/);
 const view = api.html(k, { quick: 'attention' });
 assert.match(view, /data-kpi-view="attention" aria-pressed="true"/); assert.doesNotMatch(view, /data-kpi-reset/);
 assert.match(api.html(k, { owner: 'mine' }), /data-kpi-view="mine" aria-pressed="true"/);
 assert.match(base, /role="button" tabindex="0" data-kpi="open"/);
});
