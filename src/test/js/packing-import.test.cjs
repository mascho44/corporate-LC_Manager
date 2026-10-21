const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/packing-import.js', 'utf8');
const context = vm.createContext({ tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m), TextDecoder, Uint8Array });
context.globalThis = context;
vm.runInContext(source, context);
const api = context.LcPackingImport;
const plain = value => JSON.parse(JSON.stringify(value));

test('numbers: German, English and unit-suffixed notation', () => {
 assert.equal(api.parseNumber('1.234,56'), 1234.56); assert.equal(api.parseNumber('1,234.56'), 1234.56); assert.equal(api.parseNumber('12,5'), 12.5);
 assert.equal(api.parseNumber('1,234'), 1234); assert.equal(api.parseNumber('1.234.567'), 1234567); assert.equal(api.parseNumber('12.5 kg'), 12.5);
 assert.equal(api.parseNumber('€ 99,90'), 99.9); assert.equal(api.parseNumber('1.250', 'en'), 1.25); assert.equal(api.parseNumber('1.250', 'de'), 1250); assert.equal(api.parseNumber('abc'), null); assert.equal(api.parseNumber(''), null);
});
test('delimiter detection and quoted cells with delimiter, quotes and line breaks', () => {
 assert.equal(api.detectDelimiter('a;b;c\n1;2;3'), ';'); assert.equal(api.detectDelimiter('a,b,c\n1,2,3'), ','); assert.equal(api.detectDelimiter('a\tb\tc\n1\t2\t3'), '\t');
 assert.equal(api.detectDelimiter('Beschreibung;Menge\n"Schrauben, M8";"1.000,5"'), ';');
 const rows = api.parseCsv('Beschreibung;Menge\r\n"Teil ""A""; groß";5\r\n"zwei\nZeilen";6\r\n\r\n', ';');
 assert.deepEqual(plain(rows), [['Beschreibung', 'Menge'], ['Teil "A"; groß', '5'], ['zwei\nZeilen', '6']]);
});
test('encoding: UTF-8 and Windows-1252 exports', () => {
 assert.equal(api.decode(new TextEncoder().encode('﻿Größe;Menge').buffer), 'Größe;Menge');
 assert.equal(api.decode(Uint8Array.from([0x47, 0x72, 0xF6, 0xDF, 0x65]).buffer), 'Größe');
});
test('column guess from German and English headers, each column once', () => {
 assert.deepEqual(plain(api.guessMapping(['Pos.', 'Warenbezeichnung', 'Menge', 'ME', 'Nettogewicht (kg)', 'Bruttogewicht (kg)', 'Colli'])),
  { position: 0, description: 1, quantity: 2, unit: 3, packages: 6, netWeight: 4, grossWeight: 5 });
 assert.deepEqual(plain(api.guessMapping(['Item', 'Description', 'Qty', 'Unit', 'Unit price', 'Amount', 'Cartons', 'Net weight', 'Gross weight'])),
  { position: 0, description: 1, quantity: 2, unit: 3, unitPrice: 4, amount: 5, packages: 6, netWeight: 7, grossWeight: 8 });
});
test('items: numbers parsed, empty rows ignored, rows without description skipped with a warning, totals summed', () => {
 const mapping = { description: 0, quantity: 1, unit: 2, netWeight: 3, grossWeight: 4, packages: 5 };
 const { items, warnings } = api.buildItems([['Schrauben M8', '1.000', 'Stk', '10,5', '12,0', '2'], ['', '', '', '', '', ''], ['', '5', '', '', '', ''], ['Muttern', 'viel', 'Stk', '4,25', '5', '1']], mapping);
 assert.equal(items.length, 2); assert.equal(items[0].quantity, 1000); assert.equal(items[0].position, '1'); assert.equal(items[1].quantity, null);
 assert.equal(warnings.length, 2); assert.match(warnings[0], /ohne Beschreibung/); assert.match(warnings[1], /„viel" ist keine Zahl/);
 assert.deepEqual(plain(api.totals(items)), { packages: 3, netWeight: 14.75, grossWeight: 17 });
});
test('decimal notation of a file is read from its clear values', () => {
 assert.equal(api.detectDecimal([['12,5'], ['1.250,00']], { quantity: 0 }), 'de');
 assert.equal(api.detectDecimal([['12.5'], ['1,250.00'], ['1.250']], { quantity: 0 }), 'en');
 assert.equal(api.detectDecimal([['1.000']], { quantity: 0 }), 'de');
});
