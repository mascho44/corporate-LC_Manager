const test = require('node:test'), assert = require('node:assert');
const vm = require('node:vm'), fs = require('node:fs'), path = require('node:path');
const ctx = {}; ctx.globalThis = ctx; vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/copy-hint.js'), 'utf8'), ctx);
const h = ctx.LcCopyHint;
test('what is entered or proposed wins, otherwise the stamp text is the preselection', () => {
 assert.strictEqual(h.defaultFor({ copyNumber: 1, copyHint: { kind: 'ORIGINAL' } }), 1);
 assert.strictEqual(h.defaultFor({ copyNumber: 0, copyHint: { kind: 'COPY' } }), 0);
 assert.strictEqual(h.defaultFor({ copyNumber: null, copyHint: { kind: 'ORIGINAL' } }), 0);
 assert.strictEqual(h.defaultFor({ copyHint: { kind: 'COPY' } }), 1);
 assert.strictEqual(h.defaultFor({ copyHint: { kind: 'CONFLICT' } }), null);
 assert.strictEqual(h.defaultFor({ copyHint: { kind: 'UNKNOWN' } }), null);
 assert.strictEqual(h.defaultFor({}), null); assert.strictEqual(h.defaultFor(null), null);
});
test('the note only appears when the preselection comes from the stamp text', () => {
 assert.match(h.note({ copyHint: { kind: 'ORIGINAL' } }), /Vorbelegt aus dem Stempeltext \(Original\)/);
 assert.match(h.note({ copyHint: { kind: 'COPY' } }), /\(Copy\)/);
 assert.strictEqual(h.note({ copyNumber: 0, copyHint: { kind: 'ORIGINAL' } }), '');
 assert.strictEqual(h.note({ copyHint: { kind: 'UNKNOWN' } }), '');
});
