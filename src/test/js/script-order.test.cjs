const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs');
// Scripts that wrap show() or render() at load time must come after the app parts (app-*.js), which define them; before them they silently do nothing.
test('scripts wrapping show() or render() load after app.js', () => {
 const dir = 'src/main/resources/static/', html = fs.readFileSync(dir + 'index.html', 'utf8');
 const sources = [...html.matchAll(/<script src="\/([^"?]+)/g)].map(m => m[1]), app = sources.indexOf('app-start.js');
 assert.ok(app > 0);
 const early = sources.slice(0, app).filter(file => /typeof (show|render) === 'function'|\bshow = (async )?function|\brender = function/.test(fs.readFileSync(dir + file, 'utf8')));
 assert.deepEqual(JSON.parse(JSON.stringify(early)), []);
});
test('the app parts are loaded in the order of the former app.js, one after the other', () => {
 const html = fs.readFileSync('src/main/resources/static/index.html', 'utf8');
 const sources = [...html.matchAll(/<script src=\"\/([^\"?]+)/g)].map(m => m[1]), parts = [...require('./app-source.cjs').PARTS, 'app-start'].map(name => name + '.js');
 const first = sources.indexOf(parts[0]);
 assert.ok(first >= 0);
 assert.deepEqual(JSON.parse(JSON.stringify(sources.slice(first, first + parts.length))), parts);
});
