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
test('a mail delivery shows sender, subject, sending time and mailbox; a folder delivery shows the folder and file', () => {
 const mail = describe({ channel: 'EMAIL', source: 'mail:postfach', sender: 'einkauf@kunde.example', subject: 'Dokumente zu LC 4711', scannedAt: '2026-10-17T09:30:00', reference: '<abc@kunde.example>' });
 assert.match(mail.text, /^Eingang: E-Mail von einkauf@kunde\.example · Betreff „Dokumente zu LC 4711“ · gesendet 17\.10\.26/); assert.match(mail.text, /Postfach postfach$/); assert.equal(mail.warning, null);
 assert.equal(describe({ channel: 'EMAIL', source: 'mail:x' }).text, 'Eingang: E-Mail · Postfach x');
 const folder = describe({ channel: 'FOLDER', source: 'folder:poststelle', scanner: 'poststelle', reference: 'poststelle/rechnung.pdf', scannedAt: '2026-10-17T09:31:00' });
 assert.match(folder.text, /^Eingang: Ordner poststelle · Datei rechnung\.pdf · abgelegt 17\.10\.26/);
 assert.match(describe({ channel: 'SCAN_STATION', source: 'capture:Poststelle Scanner 2', scanner: 'Scanner 2' }).text, /^Eingang: Poststelle Scanner 2 · Gerät Scanner 2/);
});
