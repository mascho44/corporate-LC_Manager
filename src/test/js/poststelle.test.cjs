const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/poststelle.js', 'utf8'), context);
const { describe, httpMessage, defaultBatch } = context.LcPoststelle;
test('receipts become clear lines: delivered, duplicate and page mismatch are told apart', () => {
 assert.equal(describe({ status: 'CREATED', pageCountMatches: true }).kind, 'ok');
 assert.match(describe({ status: 'DUPLICATE' }).text, /Schon früher/);
 const mismatch = describe({ status: 'CREATED', pageCountMatches: false, actualPages: 3 });
 assert.equal(mismatch.kind, 'warn'); assert.match(mismatch.text, /erkannt 3/);
 assert.deepEqual({ ...describe(null, 'Fehler') }, { kind: 'fail', text: 'Fehler' });
});
test('errors speak plain German; the batch defaults to today', () => {
 assert.match(httpMessage(401, null), /ungültig oder wurde widerrufen/);
 assert.equal(httpMessage(400, { error: 'Prüfwert stimmt nicht.' }), 'Prüfwert stimmt nicht.');
 assert.match(httpMessage(500, null), /Fehler 500/);
 assert.equal(defaultBatch(new Date(2026, 9, 4)), '2026-10-04');
});
