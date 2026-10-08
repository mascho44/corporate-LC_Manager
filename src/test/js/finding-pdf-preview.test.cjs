const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/finding-evidence.js','utf8');
function setup(failed=false){
 const nodes={},calls=[],revoked=[];const dialog={open:true,onclose:null};
 for(const selector of ['img','[data-status]','[data-prev]','[data-next]','[data-retry]','[data-page]','[data-zoom]'])nodes[selector]={textContent:'',disabled:false,classList:{toggle:()=>true}};
 Object.defineProperty(nodes.img,'src',{set(value){this.url=value;this.onload?.();}});
 const container={querySelector:s=>nodes[s]};
 const context=vm.createContext({AbortController,encodeURIComponent,URL:{createObjectURL:()=>`blob:page-${calls.length}`,revokeObjectURL:url=>revoked.push(url)},fetch:async url=>{calls.push(url);return {ok:!failed,status:failed?503:200,headers:{get:name=>name==='Content-Type'?'image/png':'3'},blob:async()=>({})};}});
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
