const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/finding-evidence.js','utf8');
function setup(failed=false){
 const nodes={},calls=[],revoked=[];const dialog={open:true,onclose:null};
 for(const selector of ['img','[data-status]','[data-prev]','[data-next]','[data-retry]','[data-page]','[data-zoom]'])nodes[selector]={textContent:'',disabled:false,classList:{toggle:()=>true}};
 Object.defineProperty(nodes.img,'src',{set(value){this.url=value;this.onload?.();}});
 const container={querySelector:s=>nodes[s]};
 const context=vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),AbortController,encodeURIComponent,URL:{createObjectURL:()=>`blob:page-${calls.length}`,revokeObjectURL:url=>revoked.push(url)},fetch:async url=>{calls.push(url);return {ok:!failed,status:failed?503:200,headers:{get:name=>name==='Content-Type'?'image/png':'3'},blob:async()=>({})};}});
 vm.runInContext(source.slice(source.indexOf('async function renderFindingPdf('),source.indexOf("document.addEventListener('click'")),context);
 return {run:()=>context.renderFindingPdf(dialog,container,'synthetic',1),dialog,nodes,calls,revoked};
}
test('PDF evidence uses raster pages, navigation and cleanup rather than iframe',async()=>{
 const state=setup();await state.run();assert.equal(state.nodes.img.hidden,false);assert.equal(state.nodes['[data-page]'].textContent,'Seite 1 / 3');assert.equal(state.nodes['[data-prev]'].disabled,true);assert.equal(state.nodes['[data-next]'].disabled,false);
 await state.nodes['[data-next]'].onclick();assert.match(state.calls[1],/pages\/2\/preview$/);assert.equal(state.nodes['[data-page]'].textContent,'Seite 2 / 3');assert.equal(state.revoked.length,1);
 state.dialog.onclose();assert.equal(state.revoked.length,2);assert.doesNotMatch(source,/<iframe/);
});
test('preview HTTP error is visible and leaves a working retry button',async()=>{
 const state=setup(true);await state.run();assert.match(state.nodes['[data-status]'].textContent,/HTTP 503/);assert.equal(state.nodes['[data-retry]'].disabled,false);await state.nodes['[data-retry]'].onclick();assert.equal(state.calls.length,2);assert.equal(state.nodes.img.hidden,true);
});
function setupMark(saved){
 const nodes={},calls=[];const dialog={open:true,onclose:null};
 const element=()=>({textContent:'',disabled:false,hidden:false,style:{},children:[],classList:{toggle(){return true;},add(){},remove(){},_set:new Set(),contains(){return false;}},append(...items){this.children.push(...items);},replaceChildren(...items){this.children=items;},setAttribute(){},getBoundingClientRect:()=>({left:0,top:0,width:100,height:200})});
 for(const selector of ['img','[data-status]','[data-prev]','[data-next]','[data-retry]','[data-page]','[data-zoom]','.evidence-page-wrap','[data-mark]','[data-mark-clear]'])nodes[selector]=element();
 nodes['[data-mark-clear]'].hidden=true;
 Object.defineProperty(nodes.img,'src',{set(value){this.url=value;this.hidden=false;this.onload?.();}});
 const container={querySelector:s=>nodes[s]};
 const context=vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),document:{createElement:()=>element()},AbortController,encodeURIComponent,URL:{createObjectURL:()=>`blob:page-${calls.length}`,revokeObjectURL:()=>{}},fetch:async url=>{calls.push(url);return {ok:true,status:200,headers:{get:name=>name==='Content-Type'?'image/png':'3'},blob:async()=>({})};}});
 vm.runInContext(source.slice(source.indexOf('async function renderFindingPdf('),source.indexOf("document.addEventListener('click'")),context);
 return {run:()=>context.renderFindingPdf(dialog,container,'doc-1',1,[],saved),dialog,nodes};
}
test('a saved evidence mark of the shown page is displayed (green) after loading, and a new mark can be drawn and read back',async()=>{
 const state=setupMark({documentId:'doc-1',page:1,x:0.1,y:0.2,w:0.3,h:0.05});await state.run();
 const box=state.nodes['.evidence-page-wrap'].children[0];
 assert.equal(box.hidden,false);assert.equal(box.style.left,'10%');assert.equal(box.style.width,'30%');
 assert.equal(state.dialog.getEvidenceMark(),null);
 const capture=state.nodes['.evidence-page-wrap'].children[1];
 capture.setPointerCapture=()=>{};capture.onpointerdown({preventDefault(){},clientX:10,clientY:20,pointerId:1});
 capture.onpointerup({clientX:60,clientY:60,pointerId:1});
 const mark=state.dialog.getEvidenceMark();assert.equal(mark.documentId,'doc-1');assert.equal(mark.page,1);assert.equal(mark.x,0.1);assert.equal(mark.w,0.5);assert.equal(state.nodes['[data-mark-clear]'].hidden,false);
 state.nodes['[data-mark-clear]'].onclick();assert.equal(state.dialog.getEvidenceMark(),null);
});
test('a saved mark of another document is not shown', async()=>{
 const state=setupMark({documentId:'other',page:1,x:0.1,y:0.2,w:0.3,h:0.05});await state.run();
 assert.equal(state.nodes['.evidence-page-wrap'].children[0].hidden,true);
});

test('decided positions get a red tick or cross on the shown page only, and none while the page loads',async()=>{
 const state=setupMark(null);await state.run();
 const layer=state.nodes['.evidence-page-wrap'].children.find(c=>c.className==='evidence-checks');
 assert.ok(layer);
 state.dialog.setCheckmarks([{page:1,x:0.1,y:0.2,w:0.3,h:0.05,kind:'ok',title:'Geprüft'},{page:1,x:0.2,y:0.5,w:0.2,h:0.04,kind:'discrepancy'},{page:2,x:0.1,y:0.1,w:0.1,h:0.1,kind:'ok'},{page:'x'}]);
 assert.equal(layer.children.length,2);
 const [tick,cross]=layer.children;assert.equal(tick.textContent,'✓');assert.equal(cross.textContent,'✗');assert.match(cross.className,/bad/);
 assert.equal(tick.style.left,'40%');assert.equal(tick.style.top,'22.5%');
 state.dialog.setCheckmarks(null);assert.equal(layer.children.length,0);
});
