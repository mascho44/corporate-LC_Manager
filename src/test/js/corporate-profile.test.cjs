const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/corporate-profile.js', 'utf8'), context);
const api = context.LcCorporateProfile;
const plain = value => JSON.parse(JSON.stringify(value));
const review = { results: [
 { severity: 'OK', code: 'DOCUMENT_PRESENT', message: 'Rechnung vorgelegt' },
 { severity: 'DISCREPANCY', code: 'MISSING_DOCUMENT', message: 'Packing List fehlt' },
 { severity: 'DISCREPANCY', code: 'AMOUNT', message: 'Betrag <b> weicht ab' },
 { severity: 'DISCREPANCY', code: 'DATE', message: 'Bereits entschieden', reviewDecision: 'ACCEPTED' },
 { severity: 'WARNING', code: 'W1', message: 'Hinweis' }] };
test('submission check: open discrepancies and missing documents block, decided ones and warnings do not', () => {
 const check = plain(api.submissionCheck(review));
 assert.equal(check.ready, false); assert.equal(check.blocking.length, 2); assert.equal(check.missing, 1); assert.equal(check.warnings, 1); assert.equal(check.total, 5);
 assert.equal(api.submissionCheck({ results: [{ severity: 'OK', code: 'X', message: 'ok' }, { severity: 'WARNING', code: 'W', message: 'w' }] }).ready, true);
 assert.equal(api.submissionCheck(null).ready, true);
});
test('card text: verdict, listed points escaped, warnings, note and the toggle', () => {
 const html = api.checkHtml(api.submissionCheck(review), false);
 assert.match(html, /Noch nicht einreichungsbereit/); assert.match(html, /2 Punkte müssen vorher geklärt werden \(davon 1 fehlendes Dokument\)/);
 assert.match(html, /Betrag &lt;b&gt; weicht ab/); assert.match(html, /1 Hinweis zur Kenntnis/); assert.match(html, /Alle Befunde anzeigen \(5\)/); assert.match(html, /Die endgültige Prüfung liegt bei der Bank/);
 assert.match(api.checkHtml(api.submissionCheck({ results: [] }), true), /✓ Einreichungsbereit[\s\S]*Befundliste ausblenden/);
});
