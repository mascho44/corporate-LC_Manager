const test = require('node:test'), assert = require('node:assert');
const vm = require('node:vm'), fs = require('node:fs'), path = require('node:path');
const ctx = {}; ctx.globalThis = ctx; vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/intake-channels.js'), 'utf8'), ctx);
const c = ctx.LcIntakeChannels;
test('a channel line names type, state and the last run', () => {
 assert.match(c.rowText({ type: 'FOLDER', name: 'poststelle', enabled: true, intervalMinutes: 2, lastRunAt: null }), /Ordner \(Scan to Folder\) · poststelle · an, alle 2 Min\. · noch nie gelaufen/);
 const text = c.rowText({ type: 'FOLDER', name: 'scanner-1', enabled: false, intervalMinutes: 5, lastRunAt: '2026-10-17T10:00:00', lastResult: '2 neu, 0 bereits vorhanden, 0 fehlgeschlagen' });
 assert.match(text, /scanner-1 · aus · zuletzt .*: 2 neu/);
 assert.strictEqual(c.failed({ lastResult: 'Fehler: Ordner nicht lesbar' }), true);
 assert.strictEqual(c.failed({ lastResult: '1 neu' }), false);
 assert.strictEqual(c.failed({}), false);
});
