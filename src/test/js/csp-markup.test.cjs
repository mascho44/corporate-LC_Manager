const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../../main/resources/static');

test('HTML pages contain no CSP-blocked inline JavaScript', () => {
  for (const file of fs.readdirSync(root).filter(name => name.endsWith('.html'))) {
    const html = fs.readFileSync(path.join(root, file), 'utf8');
    assert.doesNotMatch(html, /\son\w+\s*=/i, file);
    assert.doesNotMatch(html, /(?:href|src)\s*=\s*["']\s*javascript:/i, file);
    for (const script of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script\s*>/gi)) {
      assert.match(script[1], /\bsrc\s*=/i, file);
      assert.equal(script[2].trim(), '', file);
    }
  }
});
