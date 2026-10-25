const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/draft-check.js', 'utf8'), context);
const api = context.LcDraftCheck;

test('a draft with discrepancies says so and shows how to fix them, escaped', () => {
 const html = api.resultHtml({ textFound: true, discrepancies: 1, warnings: 1, extractedReference: 'LC-1', extractedDate: '2026-10-05',
  findings: [{ severity: 'DISCREPANCY', message: 'Notify <falsch>', advice: 'Notify wie im Akkreditiv', lcCondition: 'NOTIFY APPLICANT' }, { severity: 'WARNING', message: 'Hinweis' }] });
 assert.match(html, /1 Abweichung</); assert.match(html, /Notify &lt;falsch&gt;/); assert.match(html, /So beheben: Notify wie im Akkreditiv/); assert.match(html, /nicht gespeichert/);
});

test('a clean draft and an unreadable draft get their own verdict', () => {
 assert.match(api.resultHtml({ textFound: true, discrepancies: 0, warnings: 0, findings: [] }), /Keine Abweichung gefunden/);
 assert.match(api.resultHtml({ textFound: false, discrepancies: 0, warnings: 0, findings: [] }), /kein Text gefunden/);
});
