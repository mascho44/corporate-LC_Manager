const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({ Intl });
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/capture-info.js', 'utf8'), context);
const { describe } = context.LcCaptureInfo;
test('delivery line shows source, device, batch, pages and time without the technical prefix', () => {
 const d = describe({ source: 'capture:Poststelle Scanner 2', scanner: 'Scanner 2', batchId: '17', declaredPages: 4, actualPages: 4, pageCountMatches: true, scannedAt: '2026-10-13T08:15:00' });
 assert.match(d.text, /^Eingang: Poststelle Scanner 2 · Gerät Scanner 2 · Stapel 17 · 4 Seiten gemeldet · gescannt 13\.10\.26/); assert.equal(d.warning, null);
 assert.match(describe({ source: 'capture:x', declaredPages: 1, pageCountMatches: true }).text, /1 Seite gemeldet/);
});
test('a page count that differs from the real one is a warning; without delivery data there is nothing to show', () => {
 const d = describe({ source: 'capture:x', declaredPages: 4, actualPages: 3, pageCountMatches: false });
 assert.match(d.warning, /gemeldet 4, erkannt 3/); assert.equal(describe(null), null); assert.equal(describe(undefined), null);
});
