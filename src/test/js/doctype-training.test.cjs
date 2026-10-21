const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m), Date });
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/doctype-training.js', 'utf8'), context);
const { rowText, groupByType } = context.LcDocTypeTraining;
test('a learned example is described by its text start and who confirmed it', () => {
 assert.match(rowText({ sample: 'Wechsel Zahlen Sie', confirmedBy: 'anna', confirmedAt: '2026-10-14T08:00:00Z' }), /^„Wechsel Zahlen Sie“ · bestätigt von anna am 14\.10\.2026$/);
 assert.match(rowText({ confirmedBy: 'anna' }), /ohne Textauszug/);
});
test('examples are grouped by type in the order sent', () => {
 const groups = groupByType([{ type: 'BILL_OF_EXCHANGE', typeName: 'Wechsel', id: 1 }, { type: 'PACKING_LIST', typeName: 'Packing List', id: 2 }, { type: 'BILL_OF_EXCHANGE', typeName: 'Wechsel', id: 3 }]);
 assert.equal(groups.length, 2); assert.equal(groups[0].items.length, 2); assert.equal(groups[0].name, 'Wechsel'); assert.equal(groupByType(null).length, 0);
});
