const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs');

// The function is a plain declaration in login.js; load only that declaration so the page code does not run.
const source = fs.readFileSync('src/main/resources/static/login.js', 'utf8');
const start = source.indexOf('function visibleWorkspaces'), end = source.indexOf('\n', start);
const visibleWorkspaces = new Function(source.slice(start, end) + '; return visibleWorkspaces;')();

test('the home tenant is left out of the sign-in choice when other workspaces exist', () => {
  const home = { id: 'h', code: 'platform', name: 'platform', home: true }, firma = { id: 'f', code: 'firma', name: 'Firma', home: false }, sisw = { id: 's', code: 'sisw', name: 'SISW', home: false };
  assert.deepEqual(visibleWorkspaces([home, firma, sisw]), [firma, sisw]);
  assert.deepEqual(visibleWorkspaces([home, firma]), [firma]);
});

test('the home tenant stays when it is the only choice, and older answers without the flag are untouched', () => {
  const home = { id: 'h', code: 'platform', name: 'platform', home: true };
  assert.deepEqual(visibleWorkspaces([home]), [home]);
  assert.deepEqual(visibleWorkspaces([]), []);
  const plain = [{ id: 'a', code: 'a', name: 'A' }, { id: 'b', code: 'b', name: 'B' }];
  assert.deepEqual(visibleWorkspaces(plain), plain);
});
