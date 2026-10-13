const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/capture-tokens.js', 'utf8'), context);
test('token row shows name, prefix, creator and state, never a secret', () => {
 const text = context.LcCaptureTokens.rowText({ name: 'Poststelle Scanner 2', prefix: 'lcc_AbCd', createdAt: '2026-10-13T08:00:00', createdBy: 'admin', lastUsedAt: null, revoked: false });
 assert.match(text, /Poststelle Scanner 2 · lcc_AbCd… · angelegt/); assert.match(text, /von admin/); assert.match(text, /noch nie benutzt/);
 assert.match(context.LcCaptureTokens.rowText({ name: 'x', prefix: 'lcc_1', createdAt: '2026-10-13T08:00:00', revoked: true }), /widerrufen/);
});
