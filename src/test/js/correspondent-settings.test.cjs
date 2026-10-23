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

test('the webhook page has all its texts in both language packs and is wired in', () => {
  const hook = fs.readFileSync(dir + 'webhook-settings.js', 'utf8');
  const keys = [...hook.matchAll(/tx\('(wh\.[A-Za-z.]+)',\s*'((?:[^'\\]|\\.)*)'/g)];
  assert.ok(keys.length > 30);
  for (const [, key, text] of keys) { assert.ok(de[key], 'missing de ' + key); assert.ok(en[key], 'missing en ' + key); assert.strictEqual(de[key], text.replace(/\\'/g, "'"), 'default text differs for ' + key); }
  ['CREATED', 'UPDATED', 'DELETED', 'SECRET_ROTATED', 'TESTED', 'DELIVERY_RETRIED'].forEach(a => { assert.ok(de['audit.act.WEBHOOK_' + a]); assert.ok(en['audit.act.WEBHOOK_' + a]); });
  assert.match(fs.readFileSync(dir + 'index.html', 'utf8'), /webhook-settings\.js\?v=/);
  assert.match(fs.readFileSync(dir + 'app-admin.js', 'utf8'), /webhooks:\['webhookSection'\]/);
  assert.match(fs.readFileSync(dir + 'app-audit-training.js', 'utf8'), /setupWebhooks\(\)/);
});

test('the API key page has all its texts in both language packs and is wired in', () => {
  const page = fs.readFileSync(dir + 'api-key-settings.js', 'utf8');
  const keys = [...page.matchAll(/tx\('(apik\.[A-Za-z.]+)',\s*'((?:[^'\\]|\\.)*)'/g)];
  assert.ok(keys.length >= 18);
  for (const [, key, text] of keys) { assert.ok(de[key], 'missing de ' + key); assert.ok(en[key], 'missing en ' + key); assert.strictEqual(de[key], text.replace(/\\'/g, "'"), 'default text differs for ' + key); }
  ['READ', 'LC_CREATE', 'DOCUMENT_UPLOAD'].forEach(s => { ['apik.scope.', 'apik.scopeHint.'].forEach(p => { assert.ok(de[p + s]); assert.ok(en[p + s]); }); });
  ['CREATED', 'REVOKED'].forEach(a => { assert.ok(de['audit.act.API_KEY_' + a]); assert.ok(en['audit.act.API_KEY_' + a]); });
  assert.match(fs.readFileSync(dir + 'index.html', 'utf8'), /api-key-settings\.js\?v=/);
  assert.match(fs.readFileSync(dir + 'app-admin.js', 'utf8'), /apiKeys:\['apiKeySection'\]/);
  assert.match(fs.readFileSync(dir + 'app-audit-training.js', 'utf8'), /setupApiKeys\(\)/);
});

test('webhooks and API keys are offered to administrators only', () => {
  ['webhook-settings.js', 'api-key-settings.js'].forEach(file => assert.match(fs.readFileSync(dir + file, 'utf8'), /currentUser\?\.role !== 'ADMIN'/, file));
  const security = fs.readFileSync(path.join(__dirname, '../../main/java/de/ostms/lc/config/SecurityConfig.java'), 'utf8');
  assert.match(security, /"\/api\/webhooks","\/api\/webhooks\/\*\*"\)\.hasRole\("ADMIN"\)/);
  assert.match(security, /"\/api\/api-keys","\/api\/api-keys\/\*\*"\)\.hasRole\("ADMIN"\)/);
});

test('the SLA settings are offered to administrators only, the SLA display stays open', () => {
  assert.match(fs.readFileSync(dir + 'sla-settings.js', 'utf8'), /currentUser\?\.role !== 'ADMIN'/);
  const security = fs.readFileSync(path.join(__dirname, '../../main/java/de/ostms/lc/config/SecurityConfig.java'), 'utf8');
  assert.match(security, /GET,"\/api\/sla\/rules","\/api\/sla\/catalog","\/api\/sla\/report"\)\.hasRole\("ADMIN"\)/);
  ['POST', 'PUT', 'DELETE'].forEach(m => assert.match(security, new RegExp(m + ',"/api/sla/\\*\\*"\\)\\.hasRole\\("ADMIN"\\)')));
  assert.ok(!/"\/api\/lcs\/\*\/sla"[^\n]*hasRole/.test(security));
});

test('the payment block has all its texts in both language packs and is wired into the file view', () => {
  const page = fs.readFileSync(dir + 'payments.js', 'utf8');
  const keys = [...page.matchAll(/tx\('(pay\.[A-Za-z.]+)',\s*'((?:[^'\\]|\\.)*)'/g)];
  assert.ok(keys.length >= 18);
  for (const [, key, text] of keys) { assert.ok(de[key], 'missing de ' + key); assert.ok(en[key], 'missing en ' + key); assert.strictEqual(de[key], text.replace(/\\'/g, "'"), 'default text differs for ' + key); }
  ['apik.scope.PAYMENT', 'apik.scopeHint.PAYMENT', 'audit.act.LC_PAYMENT_RECORDED', 'audit.act.LC_PAYMENT_DELETED'].forEach(k => { assert.ok(de[k], k); assert.ok(en[k], k); });
  assert.match(fs.readFileSync(dir + 'index.html', 'utf8'), /payments\.js\?v=/);
  assert.match(fs.readFileSync(dir + 'app-workspace.js', 'utf8'), /LcPayments\?\.render\(block,id\)/);
});

test('the liquidity page has all its texts in both language packs and is wired in', () => {
  const page = fs.readFileSync(dir + 'liquidity.js', 'utf8');
  const keys = [...page.matchAll(/tx\('(liq\.[A-Za-z.]+)',\s*'((?:[^'\\]|\\.)*)'/g)];
  assert.ok(keys.length >= 18);
  for (const [, key, text] of keys) { assert.ok(de[key], 'missing de ' + key); assert.ok(en[key], 'missing en ' + key); assert.strictEqual(de[key], text.replace(/\\'/g, "'"), 'default text differs for ' + key); }
  assert.match(fs.readFileSync(dir + 'index.html', 'utf8'), /liquidity\.js\?v=/);
  assert.match(fs.readFileSync(dir + 'app-admin.js', 'utf8'), /liquidity:\['liquiditySection'\]/);
  assert.match(fs.readFileSync(dir + 'app-audit-training.js', 'utf8'), /setupLiquidity\(\)/);
});

test('the guarantee page has all its texts in both language packs and is wired in', () => {
  const page = fs.readFileSync(dir + 'guarantees.js', 'utf8');
  const keys = [...page.matchAll(/tx\('((?:gar|cl)\.[A-Za-z.]+)',\s*'((?:[^'\\]|\\.)*)'/g)];
  assert.ok(keys.length >= 90);
  for (const [, key, text] of keys) { assert.ok(de[key], 'missing de ' + key); assert.ok(en[key], 'missing en ' + key); assert.strictEqual(de[key], text.replace(/\\'/g, "'"), 'default text differs for ' + key); }
  ['ADVANCE_PAYMENT', 'PERFORMANCE', 'WARRANTY', 'BID', 'PAYMENT', 'CUSTOMS', 'RENT', 'OTHER'].forEach(t => { assert.ok(de['gar.type.' + t], t); assert.ok(en['gar.type.' + t], t); });
  ['REQUESTED', 'ACTIVE', 'RETURNED', 'CLAIMED', 'EXPIRED'].forEach(s => { assert.ok(de['gar.status.' + s], s); assert.ok(en['gar.status.' + s], s); });
  ['CREATED', 'UPDATED', 'STATUS_CHANGED'].forEach(a => { assert.ok(de['audit.act.GUARANTEE_' + a], a); assert.ok(en['audit.act.GUARANTEE_' + a], a); });
  ['CREATED', 'UPDATED', 'DELETED'].forEach(a => { assert.ok(de['audit.act.CREDIT_LINE_' + a], a); assert.ok(en['audit.act.CREDIT_LINE_' + a], a); });
  assert.match(fs.readFileSync(dir + 'index.html', 'utf8'), /guarantees\.js\?v=/);
  assert.match(fs.readFileSync(dir + 'app-admin.js', 'utf8'), /guarantees:\['guaranteeSection'\]/);
  assert.match(fs.readFileSync(dir + 'app-audit-training.js', 'utf8'), /setupGuarantees\(\)/);
  assert.match(fs.readFileSync(dir + 'app-workspace.js', 'utf8'), /LcGuarantees\?\.render\(block,id\)/);
  assert.match(fs.readFileSync(dir + 'app-admin.js', 'utf8'), /creditLines:\['creditLineSection'\]/);
  assert.match(fs.readFileSync(dir + 'app-audit-training.js', 'utf8'), /setupCreditLines\(\)/);
});
