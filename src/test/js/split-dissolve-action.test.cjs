const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8');
function setup(ranges,index){
 const error={textContent:''},status={textContent:''},all=[];let panel=null;
 const node=()=>({textContent:'',focus(){},onclick:null});
 for(const [from,to] of ranges){
  const fields={fromPage:{value:from,focus(){}},toPage:{value:to},documentType:{value:'OTHER'},copyNumber:{value:''}},removeButton=node();
  const row={fields,removeButton,get previousElementSibling(){return all[all.indexOf(this)-1]||null;},get nextElementSibling(){return all[all.indexOf(this)+1]||null;},querySelector(selector){if(selector==='.split-dissolve-confirm')return null;if(selector==='[data-remove]')return removeButton;return fields[selector.match(/name=(\w+)/)?.[1]];},append(p){panel=p;},contains(){return false;},remove(){all.splice(all.indexOf(this),1);}};all.push(row);
 }
 const context=vm.createContext({row:all[index],rows:{children:all},dialog:{querySelector:()=>error},submitStatus:status,preview:{},readRange:row=>({fromPage:Number(row.fields.fromPage.value),toPage:Number(row.fields.toPage.value),documentType:row.fields.documentType.value,copyNumber:null}),refreshMergeButtons(){},renderThumbs(){},document:{createElement(){const nodes={'p':node(),'[data-confirm]':node(),'[data-cancel]':node()};return {querySelector:s=>nodes[s],remove(){panel=null;},scrollIntoView(){}};}}});
 vm.runInContext(source.slice(source.indexOf('function mergeInboxSplitRanges('),source.indexOf('async function openSplitPretraining(')),context);
 const start=source.indexOf("row.querySelector('[data-remove]').onclick=()=>");const end=source.indexOf("row.querySelector('[data-preview]').onclick=",start);
 vm.runInContext(source.slice(start,end),context);
 return {all,error,status,click:()=>context.row.removeButton.onclick(),confirm:()=>panel.querySelector('[data-confirm]').onclick(),cancel:()=>panel.querySelector('[data-cancel]').onclick(),get panel(){return panel;}};
}
test('confirming dissolve actually removes the row and expands the neighbor',()=>{
 const state=setup([[1,2],[3,4],[5,6]],1);state.click();assert.ok(state.panel);state.confirm();
 assert.equal(state.all.length,2);assert.equal(state.all[0].fields.toPage.value,4);assert.match(state.status.textContent,/Bereich aufgelöst/);assert.equal(state.error.textContent,'');
});
test('cancel preserves all ranges and invalid adjacency reports an error beside confirmation',()=>{
 const cancelled=setup([[1,2],[3,4]],1);cancelled.click();cancelled.cancel();assert.equal(cancelled.all.length,2);
 const invalid=setup([[1,2],[4,4]],1);invalid.click();invalid.confirm();assert.equal(invalid.all.length,2);assert.match(invalid.panel.querySelector('p').textContent,/angrenzende/);assert.equal(invalid.error.textContent,invalid.panel.querySelector('p').textContent);
});
