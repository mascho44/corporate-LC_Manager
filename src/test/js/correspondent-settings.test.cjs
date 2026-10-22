const test = require('node:test'), assert = require('node:assert'), fs = require('node:fs'), path = require('node:path');
const dir = path.join(__dirname, '../../main/resources/static/');
const source = fs.readFileSync(dir + 'correspondent-settings.js', 'utf8');
const de = JSON.parse(fs.readFileSync(dir + 'language-de.json', 'utf8')), en = JSON.parse(fs.readFileSync(dir + 'language-en.json', 'utf8'));

test('every key of the correspondent bank page exists in both language packs, with the German default text identical', () => {
  const keys = [...source.matchAll(/tx\('(cb\.[A-Za-z.]+)',\s*'((?:[^'\\]|\\.)*)'/g)];
  assert.ok(keys.length > 25);
  for (const [, key, text] of keys) {
    assert.ok(de[key], 'missing de ' + key); assert.ok(en[key], 'missing en ' + key);
    assert.strictEqual(de[key], text.replace(/\\'/g, "'"), 'default text differs for ' + key);
  }
  ['SWIFT', 'EBICS', 'EMAIL', 'PORTAL'].forEach(c => { assert.ok(de['cb.channel.' + c]); assert.ok(en['cb.channel.' + c]); });
  ['CREATED', 'UPDATED', 'DELETED'].forEach(a => { assert.ok(de['audit.act.CORRESPONDENT_BANK_' + a]); assert.ok(en['audit.act.CORRESPONDENT_BANK_' + a]); });
});

test('the page is wired into navigation and start-up', () => {
  const html = fs.readFileSync(dir + 'index.html', 'utf8'), admin = fs.readFileSync(dir + 'app-admin.js', 'utf8'), start = fs.readFileSync(dir + 'app-audit-training.js', 'utf8');
  assert.match(html, /correspondent-settings\.js\?v=/);
  assert.match(admin, /correspondent:\['correspondentSection'\]/);
  assert.match(start, /setupCorrespondentBanks\(\)/);
  assert.match(source, /appNavigate\('correspondent'/);
});
