const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/finding-evidence.js','utf8');
function el(tag){
 const classes=new Set(),n={tag,children:[],dataset:{},attrs:{},textContent:'',disabled:false,hidden:false,title:'',
  classList:{add:(...c)=>c.forEach(x=>classes.add(x)),toggle:(c,on)=>{(on===undefined?!classes.has(c):on)?classes.add(c):classes.delete(c);return classes.has(c);},contains:c=>classes.has(c)},
  setAttribute(k,v){this.attrs[k]=v;},append(...c){this.children.push(...c);},querySelectorAll(sel){return sel==='.evidence-page-thumb'?this.children.filter(c=>c.className==='evidence-page-thumb'):[];},scrollIntoView(){}};
 return n;
}
function setup(pages=3,marked=[2],withLc=false){
 const nodes={},calls=[],listeners={};
 for(const selector of ['img','[data-status]','[data-prev]','[data-next]','[data-retry]','[data-page]','[data-zoom]'])nodes[selector]={textContent:'',disabled:false,classList:{toggle:()=>true}};
 nodes['.evidence-pages']=el('nav');nodes['.evidence-pages'].hidden=true;
 Object.defineProperty(nodes.img,'src',{set(v){this.url=v;this.onload?.();}});
 const dialog={open:true,onclose:null,addEventListener:(t,f)=>{listeners[t]=f;},removeEventListener:(t)=>{delete listeners[t];}};
 const container={querySelector:s=>nodes[s]};
 const context=vm.createContext({AbortController,encodeURIComponent,document:{createElement:el},URL:{createObjectURL:()=>'blob:x',revokeObjectURL(){}},
  ...(withLc?{activeLc:{id:'lc-1'}}:{}),
  fetch:async url=>{calls.push(url);if(String(url).includes('finding-pages'))return {ok:true,json:async()=>[{page:3,count:2,severity:'DISCREPANCY'},{page:2,count:1,severity:'WARNING'}]};return {ok:true,status:200,headers:{get:n=>n==='Content-Type'?'image/png':String(pages)},blob:async()=>({})};}});
 vm.runInContext(source.slice(source.indexOf('async function renderFindingPdf('),source.indexOf("document.addEventListener('click'")),context);
 return {run:()=>context.renderFindingPdf(dialog,container,'doc-1',2,marked),nodes,calls,listeners,dialog};
}
const thumbs=s=>s.nodes['.evidence-pages'].children;
const flush=()=>new Promise(r=>setImmediate(r));
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

test('other pages with open findings get a marker with the worst severity; the finding page keeps its own marker',async()=>{
 const s=setup(3,[2],true);
 // the sidebar looks pages up by attribute selector
 s.nodes['.evidence-pages'].querySelector=sel=>{const m=/data-page="(\d+)"/.exec(sel);return m?s.nodes['.evidence-pages'].children.find(c=>String(c.dataset.page)===m[1]):null;};
 await s.run();await flush();await flush();
 assert.ok(s.calls.some(u=>/\/api\/lcs\/lc-1\/documents\/doc-1\/finding-pages$/.test(u)));
 assert.equal(thumbs(s)[2].classList.contains('has-other'),true);assert.equal(thumbs(s)[2].classList.contains('other-bad'),true);
 assert.match(thumbs(s)[2].title,/2 offene Befunde/);
 assert.equal(thumbs(s)[1].classList.contains('has-other'),false,'the finding page is not marked twice');
 assert.equal(thumbs(s)[0].classList.contains('has-other'),false);
});
test('without an active dossier no extra request is made',async()=>{
 const s=setup();await s.run();await flush();assert.equal(s.calls.some(u=>String(u).includes('finding-pages')),false);
});
