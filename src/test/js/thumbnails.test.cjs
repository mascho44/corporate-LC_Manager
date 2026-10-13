const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/thumbnails.js', 'utf8'), context);
const api = context.LcThumbnails;
test('thumbnails only for PDFs, with the cached small and the large first page', () => {
 assert.equal(api.isPdf('Invoice.PDF'), true); assert.equal(api.isPdf('scan.png'), false); assert.equal(api.isPdf(null), false);
 assert.equal(api.documentThumbUrl('a b'), '/api/documents/a%20b/pages/1/preview?size=thumb');
 assert.equal(api.documentLargeUrl('x'), '/api/documents/x/pages/1/preview');
 assert.equal(api.inboxThumbUrl('i'), '/api/inbox/i/pages/1/preview?enlarged=false');
 assert.equal(api.inboxLargeUrl('i'), '/api/inbox/i/pages/1/preview?enlarged=true');
 assert.equal(api.templateOutlineUrl('t 1'), '/api/document-templates/t%201/outline');
});
