const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs');
// Scripts that wrap show() or render() at load time must come after app.js, which defines them; before it they silently do nothing.
test('scripts wrapping show() or render() load after app.js', () => {
 const dir = 'src/main/resources/static/', html = fs.readFileSync(dir + 'index.html', 'utf8');
 const sources = [...html.matchAll(/<script src="\/([^"?]+)/g)].map(m => m[1]), app = sources.indexOf('app.js');
 assert.ok(app > 0);
 const early = sources.slice(0, app).filter(file => /typeof (show|render) === 'function'|\bshow = (async )?function|\brender = function/.test(fs.readFileSync(dir + file, 'utf8')));
 assert.deepEqual(JSON.parse(JSON.stringify(early)), []);
});
