const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const dir = path.join(__dirname, '../../main/resources/static');

function run(stored) {
  const attributes = {};
  const sandbox = { localStorage: { getItem: () => { if (stored === 'throw') throw new Error('blocked'); return stored; } }, document: { documentElement: { setAttribute: (k, v) => { attributes[k] = v; } } } };
  vm.createContext(sandbox); vm.runInContext(fs.readFileSync(path.join(dir, 'theme-init.js'), 'utf8'), sandbox);
  return attributes;
}

test('a stored choice is applied before the page is drawn', () => {
  assert.equal(JSON.stringify(run('dark')), JSON.stringify({ 'data-theme': 'dark' }));
  assert.equal(JSON.stringify(run('light')), JSON.stringify({ 'data-theme': 'light' }));
  assert.equal(JSON.stringify(run('system')), JSON.stringify({ 'data-theme': 'system' }));
});
test('no choice, an unknown value or blocked storage leave the light theme in place', () => {
  assert.equal(JSON.stringify(run(null)), '{}');
  assert.equal(JSON.stringify(run('purple')), '{}');
  assert.equal(JSON.stringify(run('throw')), '{}');
});
test('every colour variable used by a stylesheet has a dark value in theme.css, in both blocks', () => {
  const theme = fs.readFileSync(path.join(dir, 'theme.css'), 'utf8');
  const [explicit, system] = [theme.indexOf(':root[data-theme="dark"]{'), theme.indexOf('@media (prefers-color-scheme:dark){:root[data-theme="system"]{')];
  assert.ok(explicit >= 0 && system > explicit);
  const used = new Set();
  for (const file of fs.readdirSync(dir).filter(f => f.endsWith('.css') && f !== 'theme.css'))
    for (const m of fs.readFileSync(path.join(dir, file), 'utf8').matchAll(/var\((--lc-[a-z]+-[0-9a-z-]+),/g)) used.add(m[1]);
  assert.ok(used.size > 100);
  const missing = [...used].filter(name => !theme.slice(explicit, system).includes(name + ':') || !theme.slice(system).includes(name + ':'));
  assert.equal(missing.join(','), '');
});
test('every page that loads the stylesheet also loads the theme and the early switch', () => {
  for (const page of fs.readdirSync(dir).filter(f => f.endsWith('.html'))) {
    const html = fs.readFileSync(path.join(dir, page), 'utf8');
    if (!html.includes('/styles.css')) continue;
    assert.ok(html.includes('/theme.css') && html.includes('/theme-init.js'), page);
  }
});
