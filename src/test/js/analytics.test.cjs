const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const dir = path.join(__dirname, '../../main/resources/static/');
const context = vm.createContext({ document: undefined, tx: (key, de, values = {}) => String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m) });
context.globalThis = context;
vm.runInContext(fs.readFileSync(dir + 'analytics.js', 'utf8'), context);
const A = context.LcAnalytics;
test('the axis ends on a tidy number', () => {
 assert.equal(A.niceMax(0), 1); assert.equal(A.niceMax(7), 10); assert.equal(A.niceMax(13), 20); assert.equal(A.niceMax(34), 50); assert.equal(A.niceMax(120), 200); assert.equal(A.niceMax(2), 2);
});
test('the scale has whole-number ticks and a tidy end', () => {
 for (const v of [1, 3, 4, 5, 7, 12, 34, 99, 480, 1900500]) { const s = A.niceScale(v); assert.ok(s.max >= v, 'covers ' + v); assert.ok(s.ticks >= 2 && s.ticks <= 8, 'ticks ' + v); assert.ok(Math.abs(s.max / s.ticks - s.step) < 1e-9); }
 assert.equal(A.niceScale(0).ticks, 4); assert.equal(A.niceScale(10).max, 10);
});
test('weeks are numbered by ISO and months labelled by name', () => {
 assert.equal(A.isoWeek('2026-10-19'), 43); assert.equal(A.isoWeek('2027-01-04'), 1);
 assert.equal(A.labelOf({ start: '2026-10-19' }, true), 'KW 43');
 assert.equal(A.labelOf({ start: '2026-10-01' }, false), 'Okt 26');
});
test('the line never swings past a low point or a flat stretch', () => {
 const d = A.smooth([[0, 100], [10, 100], [20, 100], [30, 0], [40, 100]]);
 const ys = [...d.matchAll(/C([\d.-]+),([\d.-]+) ([\d.-]+),([\d.-]+)/g)].flatMap(m => [Number(m[2]), Number(m[4])]);
 assert.ok(ys.every(y => y >= 0 && y <= 100), 'control points stay between the extremes: ' + ys.join(','));
 const flat = A.smooth([[0, 50], [10, 50], [20, 50]]);
 assert.ok([...flat.matchAll(/C[\d.-]+,([\d.-]+) [\d.-]+,([\d.-]+)/g)].every(m => Number(m[1]) === 50 && Number(m[2]) === 50));
});
test('the smooth line goes through every point', () => {
 const d = A.smooth([[0, 10], [10, 0], [20, 10]]);
 assert.ok(d.startsWith('M0,10')); assert.equal((d.match(/C/g) || []).length, 2); assert.ok(d.trim().endsWith('20,10'));
 assert.equal(A.smooth([]), ''); assert.equal(A.smooth([[1, 2]]), 'M1,2');
});
test('the charts are wired into the cockpit page and the files are loaded', () => {
 const admin = fs.readFileSync(dir + 'app-admin.js', 'utf8'), html = fs.readFileSync(dir + 'index.html', 'utf8');
 assert.match(admin, /cockpit:\['cockpitSection','todaySection','analyticsSection','workQueueSection','lcSection'\]/);
 assert.ok(html.includes('/analytics.js') && html.includes('/analytics.css'));
 const de = JSON.parse(fs.readFileSync(dir + 'language-de.json', 'utf8')), en = JSON.parse(fs.readFileSync(dir + 'language-en.json', 'utf8'));
 [...fs.readFileSync(dir + 'analytics.js', 'utf8').matchAll(/tr\('(an\.[A-Za-z0-9]+)'/g)].forEach(m => { assert.ok(de[m[1]], 'de ' + m[1]); assert.ok(en[m[1]], 'en ' + m[1]); });
});
