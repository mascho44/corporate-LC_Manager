const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/capture-tokens.js', 'utf8'), context);
test('token row shows name, prefix, creator and state, never a secret', () => {
 const text = context.LcCaptureTokens.rowText({ name: 'Poststelle Scanner 2', prefix: 'lcc_AbCd', createdAt: '2026-10-13T08:00:00', createdBy: 'admin', lastUsedAt: null, revoked: false });
 assert.match(text, /Poststelle Scanner 2 · lcc_AbCd… · angelegt/); assert.match(text, /von admin/); assert.match(text, /noch nie benutzt/);
 assert.match(context.LcCaptureTokens.rowText({ name: 'x', prefix: 'lcc_1', createdAt: '2026-10-13T08:00:00', revoked: true }), /widerrufen/);
});
test('silent stations give one clear warning; none means no warning', () => {
 assert.equal(context.LcCaptureTokens.silentWarning([]), null);
 const text = context.LcCaptureTokens.silentWarning([{ name: 'Scanner 1', quietWorkingDays: 3, neverUsed: false }, { name: 'Scanner 2', quietWorkingDays: 1, neverUsed: true }]);
 assert.match(text, /Scanner 1 \(seit 3 Arbeitstagen\)/); assert.match(text, /Scanner 2 \(noch nie benutzt, seit 1 Arbeitstag\)/); assert.match(text, /Poststelle/);
 assert.match(context.LcCaptureTokens.rowText({ name: 'A', prefix: 'lcc_x', createdAt: '2026-10-13T08:00:00', lastUsedAt: null, revoked: false, quietWorkingDays: 2 }), /ruhig seit 2 Arbeitstagen/);
});
