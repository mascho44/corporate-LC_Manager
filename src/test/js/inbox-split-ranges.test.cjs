const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8');const context=vm.createContext({});
vm.runInContext(source.slice(source.indexOf('function mergeInboxSplitRanges('),source.indexOf('async function openInboxSplit(')),context);
test('merging adjacent ranges retains prior type and does not mutate input',()=>{const a={fromPage:3,toPage:4,documentType:'COMMERCIAL_INVOICE'},b={fromPage:5,toPage:6,documentType:'OTHER'};const result=context.mergeInboxSplitRanges(a,b);assert.equal(result.fromPage,3);assert.equal(result.toPage,6);assert.equal(result.documentType,a.documentType);assert.equal(a.toPage,4);});
test('merge refuses gaps, overlaps, invalid numbers and reversed ranges',()=>{for(const b of [{fromPage:4,toPage:6},{fromPage:6,toPage:7},{fromPage:5,toPage:4},{fromPage:5.5,toPage:6}])assert.throws(()=>context.mergeInboxSplitRanges({fromPage:3,toPage:4},b));});
test('adding a range divides existing pages without overlap and clears designation on new part',()=>{
 const part={fromPage:3,toPage:7,documentType:'BILL_OF_LADING',copyNumber:-2};
 const divided=context.divideInboxSplitRange(part);
 assert.deepEqual(Array.from(divided,p=>[p.fromPage,p.toPage]),[[3,6],[7,7]]);
 assert.equal(divided[0].copyNumber,-2);assert.equal(divided[1].copyNumber,null);assert.equal(part.toPage,7);
 assert.throws(()=>context.divideInboxSplitRange({fromPage:2,toPage:2}));
});
const ranges=()=>[{fromPage:1,toPage:2,documentType:'OTHER'},{fromPage:3,toPage:4,documentType:'COMMERCIAL_INVOICE'},{fromPage:5,toPage:8,documentType:'PACKING_LIST'}];
test('dissolving first, middle and last ranges preserves every page and neighbor metadata',()=>{
 for(let index=0;index<3;index++){
  const parts=ranges();parts[0].copyNumber=-1;parts[1].copyNumber=2;
  const result=context.dissolveInboxSplitRange(parts,index);
  assert.equal(result.length,2);let next=1;for(const part of result){assert.equal(part.fromPage,next);next=part.toPage+1;}assert.equal(next,9);
  assert.equal(result[index===2?1:0].documentType,parts[index===0?1:index-1].documentType);
  assert.equal(parts.length,3);
 }
 assert.throws(()=>context.dissolveInboxSplitRange([{fromPage:1,toPage:4}],0));
 assert.throws(()=>context.dissolveInboxSplitRange([{fromPage:1,toPage:2},{fromPage:4,toPage:4}],1));
});
test('end boundary updates subsequent starts and cascades past consumed ranges',()=>{
    const parts=ranges();parts[0].toPage=4;
    const result=context.adjustInboxSplitRanges(parts,0,'toPage',8);
    assert.deepEqual(Array.from(result,p=>[p.fromPage,p.toPage]),[[1,4],[5,5],[6,8]]);
    assert.equal(result[1].documentType,'COMMERCIAL_INVOICE');assert.equal(parts[1].fromPage,3);
});
test('shrinking a range extends the next range without changing later boundaries',()=>{
    const parts=ranges();parts[0].toPage=1;
    assert.deepEqual(Array.from(context.adjustInboxSplitRanges(parts,0,'toPage',8),p=>[p.fromPage,p.toPage]),[[1,1],[2,4],[5,8]]);
});
test('editing a start adjusts the previous end and preserves full coverage',()=>{
    const parts=ranges();parts[1].fromPage=2;
    assert.deepEqual(Array.from(context.adjustInboxSplitRanges(parts,1,'fromPage',8),p=>[p.fromPage,p.toPage]),[[1,1],[2,4],[5,8]]);
});
test('invalid edits never mutate subsequent ranges or silently remove document types',()=>{
    for(const value of [7,8,9,1.5,0]){const parts=ranges();parts[0].toPage=value;assert.throws(()=>context.adjustInboxSplitRanges(parts,0,'toPage',8));assert.equal(parts[1].fromPage,3);assert.equal(parts.length,3);}
    const parts=ranges();parts[2].toPage=7;assert.throws(()=>context.adjustInboxSplitRanges(parts,2,'toPage',8));
    parts[0].fromPage=2;assert.throws(()=>context.adjustInboxSplitRanges(parts,0,'fromPage',8));
});
