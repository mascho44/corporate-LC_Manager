const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const context = vm.createContext({ globalThis: null }); context.globalThis = context;
vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/global-search.js'), 'utf8'), context);
const pages = ['Cockpit', 'LC-Akten', 'SLAs', 'Rule Packs', 'Dokumentvorlagen', 'Meine Arbeit'].map(label => ({ label }));
const labels = list => JSON.stringify(list.map(item => item.label));

test('pages are found by any word of the label, case-insensitive', () => {
  assert.equal(labels(context.LcSearch.searchPages(pages, 'sla')), JSON.stringify(['SLAs']));
  assert.equal(labels(context.LcSearch.searchPages(pages, 'VORLAGEN')), JSON.stringify(['Dokumentvorlagen']));
  assert.equal(labels(context.LcSearch.searchPages(pages, 'rule packs')), JSON.stringify(['Rule Packs']));
});
test('labels that start with the query come first', () => {
  assert.equal(labels(context.LcSearch.searchPages(pages, 'm')), JSON.stringify(['Meine Arbeit', 'Dokumentvorlagen']));
});
test('an empty query and no match give nothing, the limit is respected', () => {
  assert.equal(context.LcSearch.searchPages(pages, '').length, 0);
  assert.equal(context.LcSearch.searchPages(pages, 'zzz').length, 0);
  assert.equal(context.LcSearch.searchPages(pages, 'e', 2).length, 2);
});
