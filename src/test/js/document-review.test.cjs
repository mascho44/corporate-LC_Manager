const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/document-review.js', 'utf8'), context);
const api = context.LcDocumentReview;
const f = (code, document, severity, decision) => ({ code, documentName: document, severity, reviewDecision: decision || null, message: code });
const docs = [{ id: 'd-b', originalFilename: 'b.pdf', contentType: 'application/pdf' }, { id: 'd-a', originalFilename: 'a.pdf', contentType: 'application/pdf' }];
test('findings are grouped by document in the order of the document list, open ones first, documents without a file last', () => {
 const groups = api.groupFindings([f('X', 'a.pdf', 'WARNING'), f('Y', 'b.pdf', 'OK'), f('Z', 'a.pdf', 'DISCREPANCY'), f('W', null, 'WARNING'), f('V', 'b.pdf', 'WARNING', 'ACCEPTED'), f('U', 'c.pdf', 'OK')], docs);
 assert.deepEqual(Array.from(groups, g => g.name), ['b.pdf', 'a.pdf', '']);
 assert.deepEqual(Array.from(groups[1].items, i => i.code), ['Z', 'X']);
 assert.equal(groups[0].openCount, 0); assert.equal(groups[1].openCount, 2); assert.equal(groups[2].documentId, null); assert.equal(groups[0].documentId, 'd-b');
 assert.ok(!Array.from(groups, g => g.name).includes('c.pdf'), 'a document with only findings in order is not listed');
});
test('the next open finding and the next document with open findings are found without wrapping', () => {
 const items = [f('A', 'x', 'WARNING', 'ACCEPTED'), f('B', 'x', 'WARNING'), f('C', 'x', 'OK'), f('D', 'x', 'DISCREPANCY')];
 assert.equal(api.nextOpen(items), 1); assert.equal(api.nextOpen(items, 1), 3); assert.equal(api.nextOpen(items, 3), -1);
 assert.equal(api.nextGroupWithOpen([{ openCount: 0 }, { openCount: 2 }, { openCount: 0 }]), 1); assert.equal(api.nextGroupWithOpen([{ openCount: 0 }, { openCount: 2 }], 1), -1);
 assert.equal(api.isOpen(f('A', 'x', 'DISCREPANCY', 'CONFIRMED_DISCREPANCY')), false);
});
test('the hint that an earlier decision no longer applies is not an open finding and cannot block finishing a document', () => {
 const stale = f('REVIEW_STALE', 'a.pdf', 'WARNING');
 assert.equal(api.isOpen(stale), false);
 const groups = api.groupFindings([stale, f('X', 'a.pdf', 'WARNING', 'ACCEPTED')], docs);
 assert.equal(groups[0].openCount, 0); assert.equal(api.nextOpen(groups[0].items), -1);
 const mixed = api.groupFindings([stale, f('Y', 'a.pdf', 'DISCREPANCY')], docs);
 assert.equal(mixed[0].openCount, 1); assert.equal(mixed[0].items[api.nextOpen(mixed[0].items)].code, 'Y');
});
