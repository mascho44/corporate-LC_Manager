const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8'),context=vm.createContext({});
vm.runInContext(source.slice(source.indexOf('function documentCopyLabel('),source.indexOf('let inboxRefreshTimer=')),context);
test('unspecified is never inferred as original and numeric zero survives form conversion',()=>{
 assert.equal(context.readDocumentCopy(''),null);assert.equal(context.readDocumentCopy('0'),0);assert.equal(context.readDocumentCopy('3'),3);
 assert.equal(context.documentCopyLabel(null),'Nicht gekennzeichnet');assert.equal(context.documentCopyLabel(0),'Original');assert.equal(context.documentCopyLabel(2),'Copy 2');
});
test('editing selects the stored designation, not a default original',()=>{
 assert.match(context.documentCopyOptions(null),/value="" selected/);assert.match(context.documentCopyOptions(0),/value="0" selected/);assert.match(context.documentCopyOptions(3),/value="3" selected/);
});
test('numbered originals remain distinct from copies',()=>{
 for(let ordinal=1;ordinal<=3;ordinal++){
  assert.equal(context.readDocumentCopy(String(-ordinal)),-ordinal);
  assert.equal(context.documentCopyLabel(-ordinal),'Original '+ordinal);
  assert.match(context.documentCopyOptions(-ordinal),new RegExp('value="-'+ordinal+'" selected'));
 }
});
