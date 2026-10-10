const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/finding-evidence.js','utf8');
function el(tag){
 const classes=new Set(),n={tag,children:[],dataset:{},attrs:{},textContent:'',disabled:false,hidden:false,title:'',
  classList:{add:c=>classes.add(c),toggle:(c,on)=>{(on===undefined?!classes.has(c):on)?classes.add(c):classes.delete(c);return classes.has(c);},contains:c=>classes.has(c)},
  setAttribute(k,v){this.attrs[k]=v;},append(...c){this.children.push(...c);},querySelectorAll(sel){return sel==='.evidence-page-thumb'?this.children.filter(c=>c.className==='evidence-page-thumb'):[];},scrollIntoView(){}};
 return n;
}
function setup(pages=3,marked=[2]){
 const nodes={},calls=[],listeners={};
 for(const selector of ['img','[data-status]','[data-prev]','[data-next]','[data-retry]','[data-page]','[data-zoom]'])nodes[selector]={textContent:'',disabled:false,classList:{toggle:()=>true}};
 nodes['.evidence-pages']=el('nav');nodes['.evidence-pages'].hidden=true;
 Object.defineProperty(nodes.img,'src',{set(v){this.url=v;this.onload?.();}});
 const dialog={open:true,onclose:null,addEventListener:(t,f)=>{listeners[t]=f;},removeEventListener:(t)=>{delete listeners[t];}};
 const container={querySelector:s=>nodes[s]};
 const context=vm.createContext({AbortController,encodeURIComponent,document:{createElement:el},URL:{createObjectURL:()=>'blob:x',revokeObjectURL(){}},
  fetch:async url=>{calls.push(url);return {ok:true,status:200,headers:{get:n=>n==='Content-Type'?'image/png':String(pages)},blob:async()=>({})};}});
 vm.runInContext(source.slice(source.indexOf('async function renderFindingPdf('),source.indexOf("document.addEventListener('click'")),context);
 return {run:()=>context.renderFindingPdf(dialog,container,'doc-1',2,marked),nodes,calls,listeners,dialog};
}
const thumbs=s=>s.nodes['.evidence-pages'].children;
test('sidebar lists all pages, marks the finding page and highlights the selected one',async()=>{
 const s=setup();await s.run();
 assert.equal(s.nodes['.evidence-pages'].hidden,false);assert.equal(thumbs(s).length,3);
 assert.equal(thumbs(s)[1].classList.contains('has-finding'),true);assert.equal(thumbs(s)[0].classList.contains('has-finding'),false);
 assert.match(thumbs(s)[1].attrs['aria-label'],/hier liegt der Befund/);
 assert.equal(thumbs(s)[1].classList.contains('selected'),true);assert.equal(thumbs(s)[1].attrs['aria-current'],'page');
 assert.match(thumbs(s)[0].children[0].dataset.src,/pages\/1\/preview\?size=thumb$/);
});
test('clicking a thumbnail loads that page and moves the selection',async()=>{
 const s=setup();await s.run();await thumbs(s)[2].onclick();
 assert.match(s.calls.at(-1),/pages\/3\/preview$/);assert.equal(thumbs(s)[2].classList.contains('selected'),true);assert.equal(thumbs(s)[1].classList.contains('selected'),false);
});
test('arrow keys, page keys, Home and End navigate; inputs and modifiers are ignored',async()=>{
 const s=setup();await s.run();const key=(k,extra={})=>s.listeners.keydown({key:k,preventDefault(){},target:{tagName:'DIV'},...extra});
 await key('ArrowRight');assert.match(s.calls.at(-1),/pages\/3\/preview$/);
 const before=s.calls.length;await key('ArrowRight');assert.equal(s.calls.length,before,'no page after the last');
 await key('Home');assert.match(s.calls.at(-1),/pages\/1\/preview$/);
 await key('End');assert.match(s.calls.at(-1),/pages\/3\/preview$/);
 const n=s.calls.length;await key('ArrowLeft',{target:{tagName:'TEXTAREA'}});await key('ArrowLeft',{ctrlKey:true});assert.equal(s.calls.length,n);
});
test('keyboard handler is removed when the dialog closes; single pages get no sidebar',async()=>{
 const s=setup();await s.run();assert.ok(s.listeners.keydown);s.dialog.onclose();assert.equal(s.listeners.keydown,undefined);
 const one=setup(1,[]);await one.run();assert.equal(one.nodes['.evidence-pages'].hidden,true);
});
