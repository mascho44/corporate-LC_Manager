const test = require('node:test'), assert = require('node:assert/strict'), vm = require('node:vm');
const source = require('./app-source.cjs')();
const start = source.indexOf('function duplicateDocumentDraft(');
const body = source.slice(start, source.indexOf('\n', start));

function run(draft) {
  const calls = [];
  const form = { elements: { documentNumber: { value: 'INV-NEU-1' }, documentDate: { value: '2026-10-26' } } };
  const context = vm.createContext({
    $: () => form,
    openDocumentCreation: type => calls.push(['open', type]),
    fillDocumentForm: data => calls.push(['fill', JSON.stringify(data)]),
    showTemplateSelection: type => calls.push(['template', type])
  });
  vm.runInContext(body + '\nglobalThis.duplicate = duplicateDocumentDraft;', context);
  context.duplicate(draft);
  return calls;
}

test('the copy opens a new document of the same type with the proposal number and date', () => {
  const calls = run({ documentType: 'PACKING_LIST', data: { documentNumber: 'PL-ALT', documentDate: '2026-01-01', description: 'Ware', items: [{ description: 'A' }] } });
  assert.equal(calls[0].join(), 'open,PACKING_LIST');
  const filled = JSON.parse(calls[1][1]);
  assert.equal(filled.documentNumber, 'INV-NEU-1');
  assert.equal(filled.documentDate, '2026-10-26');
  assert.equal(filled.type, 'PACKING_LIST');
  assert.equal(filled.description, 'Ware');
  assert.equal(filled.items.length, 1);
});

test('no draft, nothing happens', () => {
  assert.equal(run(undefined).length, 0);
});

test('the button is offered only with the permission to create documents', () => {
  assert.match(source, /can\('DOCUMENT_GENERATE'\)&&generatedDocumentTypes\[draft\.documentType\]\?`<button[^`]*data-draft-duplicate/);
});
