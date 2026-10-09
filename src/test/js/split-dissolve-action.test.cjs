const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8');
function setup(ranges,index){
 const error={textContent:''},status={textContent:''},all=[];let panel=null;
 const node=()=>({textContent:'',focus(){},onclick:null});
 const makeRow=(from,to)=>{
  const fields={fromPage:{value:from,focus(){}},toPage:{value:to},documentType:{value:'OTHER'},copyNumber:{value:''}},removeButton=node();
  return {fields,removeButton,querySelector(selector){if(selector==='.split-dissolve-confirm')return null;if(selector==='[data-remove]')return removeButton;return fields[selector.match(/name=(\w+)/)?.[1]];},append(p){panel=p;},contains(){return false;}};
 };
 for(const [from,to] of ranges)all.push(makeRow(from,to));
 const rows={get children(){return all;},replaceChildren(){all.length=0;}};
 const context=vm.createContext({row:all[index],rows,dialog:{querySelector:()=>error},submitStatus:status,preview:{},
  readRange:row=>({fromPage:Number(row.fields.fromPage.value),toPage:Number(row.fields.toPage.value),documentType:row.fields.documentType.value,copyNumber:null}),
  refreshMergeButtons(){},add(part){const r=makeRow(part.fromPage,part.toPage);r.fields.documentType.value=part.documentType;all.push(r);},
  document:{createElement(){const nodes={'p':node(),'[data-confirm]':node(),'[data-cancel]':node()};return {querySelector:s=>nodes[s],remove(){panel=null;},scrollIntoView(){}};}}});
 vm.runInContext(source.slice(source.indexOf('function mergeInboxSplitRanges('),source.indexOf('async function openSplitPretraining(')),context);
 const start=source.indexOf("row.querySelector('[data-remove]').onclick=()=>");const end=source.indexOf("row.querySelector('[data-preview]').onclick=",start);
 vm.runInContext(source.slice(start,end),context);
 return {all,error,status,click:()=>context.row.removeButton.onclick(),confirm:()=>panel.querySelector('[data-confirm]').onclick(),cancel:()=>panel.querySelector('[data-cancel]').onclick(),get panel(){return panel;}};
}
test('confirming splits the range into single pages and leaves neighbors untouched',()=>{
 const state=setup([[1,2],[3,5],[6,6]],1);state.click();assert.ok(state.panel);state.confirm();
 assert.deepEqual(state.all.map(r=>[r.fields.fromPage.value,r.fields.toPage.value]),[[1,2],[3,3],[4,4],[5,5],[6,6]]);
 assert.match(state.status.textContent,/Einzelseiten/);assert.equal(state.error.textContent,'');
});
test('cancel keeps all ranges and a single page cannot be dissolved',()=>{
 const cancelled=setup([[1,2],[3,4]],1);cancelled.click();cancelled.cancel();assert.equal(cancelled.all.length,2);
 const single=setup([[1,2],[3,3]],1);single.click();assert.equal(single.panel,null);assert.match(single.error.textContent,/nur aus einer Seite/);
});
