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

test('findings are sorted into blocking, check, hint and done tiers', () => {
 const { tierOf } = api;
 assert.equal(tierOf({ code: 'INVOICE_AMOUNT_EXCEEDED', severity: 'DISCREPANCY' }), 'blocking');
 assert.equal(tierOf({ code: 'SHIPMENT_DATE_EXCEEDED', severity: 'WARNING' }), 'blocking');
 assert.equal(tierOf({ code: 'MISSING_DOCUMENT', severity: 'DISCREPANCY' }), 'blocking');
 assert.equal(tierOf({ code: 'SIGNATURE_REQUIREMENT_MISSING_SUSPECTED', severity: 'WARNING' }), 'blocking');
 assert.equal(tierOf({ code: 'GOODS_DESCRIPTION', severity: 'DISCREPANCY' }), 'check');
 assert.equal(tierOf({ code: 'GOODS_DESCRIPTION', severity: 'WARNING' }), 'hint');
 assert.equal(tierOf({ code: 'INVOICE_AMOUNT_EXCEEDED', severity: 'DISCREPANCY', reviewDecision: 'ACCEPTED' }), 'done');
});

test('blocking findings come first and progress counts decidable findings over all documents', () => {
 const { groupFindings, progress } = api;
 const groups = groupFindings([
  { code: 'GOODS', severity: 'WARNING', documentName: 'a.pdf' },
  { code: 'INVOICE_AMOUNT_EXCEEDED', severity: 'DISCREPANCY', documentName: 'a.pdf' },
  { code: 'X', severity: 'DISCREPANCY', documentName: 'b.pdf', reviewDecision: 'ACCEPTED' },
  { code: 'REVIEW_STALE', severity: 'WARNING', documentName: 'b.pdf' },
  { code: 'OKAY', severity: 'OK', documentName: 'b.pdf' }
 ], [{ originalFilename: 'a.pdf', id: '1' }, { originalFilename: 'b.pdf', id: '2' }]);
 assert.equal(groups[0].items[0].code, 'INVOICE_AMOUNT_EXCEEDED');
 assert.equal(groups[0].blockingCount, 1);
 const p = progress(groups);
 assert.equal(p.total, 3); assert.equal(p.open, 2); assert.equal(p.done, 1);
});

test('the same open finding in other documents is offered for one decision', () => {
 const { siblingsOf } = api;
 const a = f('GOODS_DESCRIPTION', 'a.pdf', 'DISCREPANCY'), b = f('GOODS_DESCRIPTION', 'b.pdf', 'DISCREPANCY'), done = f('GOODS_DESCRIPTION', 'c.pdf', 'DISCREPANCY', 'ACCEPTED'), other = f('PORT', 'b.pdf', 'DISCREPANCY'), set = f('GOODS_DESCRIPTION', '', 'DISCREPANCY');
 const groups = [{ items: [a] }, { items: [b, other] }, { items: [done] }, { items: [set] }];
 assert.deepEqual(Array.from(siblingsOf(groups, a), x => x.documentName), ['b.pdf']);
 assert.equal(siblingsOf(groups, set).length, 0);
 assert.equal(siblingsOf(groups, other).length, 0);
});

test('field labels: loaded labels win, known ones stay, unknown ones are made readable', () => {
  assert.equal(api.fieldLabel('LC_PRESENTATION_DATE'), 'Vorlagedatum');
  assert.equal(api.fieldLabel('LC_SECOND_BENEFICIARY'), 'second beneficiary');
  api.setFieldLabels({ LC_SECOND_BENEFICIARY: 'Zweiter Begünstigter', LC_COVERAGE_FROM: 'Deckung ab' });
  assert.equal(api.fieldLabel('LC_SECOND_BENEFICIARY'), 'Zweiter Begünstigter');
  assert.equal(api.fieldLabel('LC_COVERAGE_FROM'), 'Deckung ab');
  assert.equal(api.fieldLabel('LC_PRESENTATION_DATE'), 'Vorlagedatum');
});
