const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/ebics.js'),'utf8');
function fixture(allowed,state){
 const calls=[],pages=[];
 function node(tag){const n={tag,children:[],textContent:'',value:'',dataset:{},elements:{},style:{},
  append(...c){this.children.push(...c);},replaceChildren(...c){this.children=c;},setAttribute(){},classList:{add(){},remove(){}}};return n;}
 const nav=node('nav'),main=node('main');
 const form={elements:{}};
 const created=[];
 const context=vm.createContext({can:()=>allowed,confirmAction:async()=>true,appNavigate:(p)=>pages.push(p),
  json:async(url,options)=>{calls.push({url,options});if(url==='/api/ebics/connection'&&!options)return state;if(url==='/api/ebics/connection/fingerprints')return {a005:'A',e002:'E',x002:'X'};return state;},
  document:{createElement:tag=>{const n=node(tag);
    if(tag==='form'){n.elements={};const orig=n.append;n.append=function(...c){orig.apply(this,c);for(const l of c){const i=(l.children||[]).find(x=>x.tag==='input');if(i){i.name=i.name||'';}}};}
    if(tag==='input'){Object.defineProperty(n,'name',{get(){return this._name;},set(v){this._name=v;if(created.length&&v)created[created.length-1].elements[v]=this;}});}
    if(tag==='form')created.push(n);return n;},
   querySelector:s=>s==='#appNav nav'?nav:s==='main'?main:null}});
 vm.runInContext(source,context);
 return {context,nav,main,calls,pages,created};
}
const unconfigured={configured:false,status:'NEW',encryptionConfigured:true};
test('nothing is added without settings permission',async()=>{const f=fixture(false,unconfigured);await f.context.setupEbics();assert.equal(f.nav.children.length,0);assert.equal(f.main.children.length,0);});
test('navigation entry loads the connection and sends trimmed identifiers',async()=>{
 const f=fixture(true,unconfigured);await f.context.setupEbics();
 assert.equal(f.nav.children.length,1);assert.equal(f.main.children.length,1);
 await f.nav.children[0].onclick();assert.deepEqual(f.pages,['ebics']);assert.ok(f.calls.some(c=>c.url==='/api/ebics/connection'&&!c.options));
 const form=f.created[0];form.elements.url.value=' https://bank.example/ebicsweb ';form.elements.hostId.value='HOST ';form.elements.partnerId.value='P1';form.elements.userId.value='U1';
 await form.onsubmit({preventDefault(){}});
 const put=f.calls.find(c=>c.options?.method==='PUT');assert.deepEqual(JSON.parse(put.options.body),{url:'https://bank.example/ebicsweb',hostId:'HOST',partnerId:'P1',userId:'U1'});
});

test('active connection offers fetching and lists messages with import only for importable ones',async()=>{
 const state={configured:true,status:'ACTIVE',encryptionConfigured:true,url:'u',hostId:'h',partnerId:'p',userId:'u'};
 const f=fixture(true,state);
 const original=f.context.json;
 f.context.json=async(url,options)=>{
  if(url==='/api/ebics/messages')return [{id:'1',messageType:'MT700',reference:'LC1',status:'NEW',importable:true,receivedAt:'2026-10-09T10:00:00'},{id:'2',messageType:'MT760',reference:'G1',status:'NEW',importable:false,receivedAt:'2026-10-09T10:01:00'}];
  return original(url,options);
 };
 f.context.can=()=>true;
 await f.context.setupEbics();await f.nav.children[0].onclick();
 const all=[];(function walk(n){all.push(n);(n.children||[]).forEach(walk);})(f.main);
 const rows=all.filter(n=>n.className==='membership-row');assert.equal(rows.length,2);
 const labels=r=>r.children.filter(c=>c.tag==='button').map(b=>b.textContent);
 assert.deepEqual(labels(rows[0]),['Ansehen','Importieren','Verwerfen']);assert.deepEqual(labels(rows[1]),[]);
});
