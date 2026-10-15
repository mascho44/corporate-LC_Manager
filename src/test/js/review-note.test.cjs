const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({}); context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/review-note.js', 'utf8'), context);
const api = context.LcReviewNote;
test('own templates come first, defaults follow without duplicates', () => {
 const list = Array.from(api.templatesFor('ACCEPTED', ['Eigene Begründung', 'original geprüft, angabe im dokument vorhanden.']));
 assert.equal(list[0], 'Eigene Begründung');
 assert.equal(list.filter(t => t.toLowerCase().startsWith('original geprüft')).length, 1);
 assert.ok(Array.from(api.templatesFor('CONFIRMED_DISCREPANCY', [])).every(t => t.startsWith('Unstimmigkeit')));
});
test('remembering moves a template to the front and keeps at most twelve', () => {
 let own = [];
 for (let i = 0; i < 15; i++) own = api.remember(own, 'Vermerk ' + i);
 assert.equal(own.length, 12); assert.equal(own[0], 'Vermerk 14');
 own = api.remember(own, 'vermerk 5'); assert.equal(own[0], 'vermerk 5'); assert.equal(own.filter(t => t.toLowerCase() === 'vermerk 5').length, 1);
 assert.equal(api.remember(own, '   ').length, own.length);
});
