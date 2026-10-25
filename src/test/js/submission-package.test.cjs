const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/submission-package.js', 'utf8'), context);
const api = context.LcSubmissionPackage;
const row = (extra) => ({ documentId: 'd-1', filename: 'inv.docx', included: true, originals: '3', copies: '0', ...extra });

test('a package needs at least one chosen document with a whole number of originals and copies', () => {
  assert.match(api.problem([]), /mindestens ein Dokument/);
  assert.match(api.problem([row({ included: false })]), /mindestens ein Dokument/);
  assert.equal(api.problem([row()]), null);
  assert.match(api.problem([row({ originals: '0', copies: '0' })]), /weder ein Original noch eine Kopie/);
  assert.match(api.problem([row({ originals: 'x' })]), /ganze Zahlen von 0 bis 99/);
  assert.match(api.problem([row({ copies: '100' })]), /ganze Zahlen von 0 bis 99/);
  assert.match(api.problem([row({ copies: '1,5' })]), /ganze Zahlen/);
  assert.equal(api.problem([row({ included: false, originals: 'x' }), row({ documentId: 'd-2', filename: 'pl.docx', originals: '1', copies: '2' })]), null);
});

test('the request keeps only the chosen documents and trims the texts', () => {
  const body = api.requestBody({ date: '2026-10-19', recipient: '  Musterbank AG\nFrankfurt ', ourReference: '  ', remarks: ' Bitte gutschreiben ', rows: [row(), row({ documentId: 'd-2', included: false }), row({ documentId: 'd-3', originals: '1', copies: '2' })] });
  assert.equal(body.recipient, 'Musterbank AG\nFrankfurt'); assert.equal(body.ourReference, null); assert.equal(body.remarks, 'Bitte gutschreiben');
  assert.deepEqual(JSON.parse(JSON.stringify(body.documents)), [{ documentId: 'd-1', originals: 3, copies: 0 }, { documentId: 'd-3', originals: 1, copies: 2 }]);
});

test('the notice names the open discrepancies and escapes the messages', () => {
  assert.equal(api.warningHtml({ warnings: [], openDiscrepancies: 0 }), '');
  const html = api.warningHtml({ warnings: ['Betrag <zu hoch> (inv.docx)', 'Versand zu spät'], openDiscrepancies: 2 });
  assert.match(html, /2 offene Abweichungen/); assert.match(html, /Betrag &lt;zu hoch&gt;/);
  assert.match(api.warningHtml({ warnings: ['Nur ein Dokument fehlt'], openDiscrepancies: 1 }), /1 offene Abweichung\./);
  assert.doesNotMatch(api.warningHtml({ warnings: ['Hinweis'], openDiscrepancies: 0 }), /offene Abweichung/);
});
