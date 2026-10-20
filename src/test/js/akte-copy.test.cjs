const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/akte-copy.js', 'utf8'), context);
const api = context.LcAkteCopy;

test('the suggested reference counts up and skips taken ones', () => {
 assert.equal(api.suggestion('LC-1', new Set()), 'LC-1-K2');
 assert.equal(api.suggestion('LC-1', new Set(['LC-1-K2'])), 'LC-1-K3');
 assert.equal(api.suggestion('LC-1-K2', new Set(['LC-1-K2'])), 'LC-1-K3');
});
