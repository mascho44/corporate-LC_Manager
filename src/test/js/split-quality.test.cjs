const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/split-quality.js','utf8');
function setup(report,status=200){
 const elements=[];function node(tag){const value={tag,children:[],textContent:'',classList:{contains:()=>true},append(...children){this.children.push(...children);},replaceChildren(){this.children=[];},setAttribute(){},addEventListener(name,fn){this[name]=fn;}};elements.push(value);return value;}
 const section=node('section');const context=vm.createContext({document:{getElementById:()=>section,createElement:node},MutationObserver:class{observe(){}},fetch:async()=>({ok:status===200,status,json:async()=>report})});vm.runInContext(source,context);
 return {elements,refresh:elements.find(e=>e.tag==='button'),text:()=>elements.map(e=>e.textContent).join('\n')};
}
test('shows denominators and treats data as plain text',async()=>{
 const state=setup({confirmations:8,measured:1,pages:4,correctedPages:2,changedBoundaries:1,recent:[{pattern:'<img onerror=evil>',method:'CONFIRM_TIME_RULE_BASED',pages:4,correctedPages:2,boundariesChanged:true,confirmedAt:'today'}]});await state.refresh.click();assert.match(state.text(),/8 confirmations · 1 measured/);assert.match(state.text(),/recomputed at confirmation/);assert.match(state.text(),/<img onerror=evil>/);assert.equal(state.refresh.disabled,false);assert.doesNotMatch(source,/innerHTML/);
});
test('empty samples and failed requests remain explicit',async()=>{
 const empty=setup({confirmations:3,measured:0,pages:0,correctedPages:0,changedBoundaries:0,recent:[]});await empty.refresh.click();assert.match(empty.text(),/No measured confirmations yet/);
 const failed=setup({},503);await failed.refresh.click();assert.match(failed.text(),/HTTP 503/);assert.equal(failed.refresh.disabled,false);
});
test('shows page ranges and document types before and after confirmation',async()=>{
 const state=setup({confirmations:1,measured:1,pages:2,correctedPages:1,changedBoundaries:0,recent:[{pattern:'abc',method:'RULE_BASED',pages:2,correctedPages:1,boundariesChanged:false,confirmedAt:'today',proposedParts:[{fromPage:1,toPage:2,documentType:'OTHER'}],confirmedParts:[{fromPage:1,toPage:2,documentType:'COMMERCIAL_INVOICE'}]}]});await state.refresh.click();assert.match(state.text(),/Proposed: 1–2: other → Confirmed: 1–2: commercial invoice/);
});
