const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
const source=fs.readFileSync(path.join(root,'scan-profile.js'),'utf8');
function el(tag){return {tag,children:[],textContent:'',value:'',disabled:false,attrs:{},append(...c){this.children.push(...c);},replaceChildren(...c){this.children=c;},setAttribute(k,v){this.attrs[k]=v;}};}
function setup(failSave=false){
 const calls=[];const view={current:'STANDARD',changedBy:null,available:[
  {id:'STANDARD',label:'Standard',description:'Bisheriges Verhalten',renderDpi:300},{id:'PROFI_SCANNER',label:'Profi-Scanner',description:'Saubere Scans',renderDpi:300}]};
 const context=vm.createContext({document:undefined,json:async(url,options)=>{calls.push({url,options});if(options?.method==='PUT'){if(failSave)throw Error('Nicht erlaubt');return {...view,current:JSON.parse(options.body).profile,changedBy:'anna'};}return view;}});
 context.globalThis=context;vm.runInContext(source,context);
 // the module only touches the document inside build(); give it a tiny one now
 context.document={createElement:el};
 return {context,calls};
}
function all(n){return [n,...n.children.flatMap(c=>c&&c.children?all(c):[c])];}
test('shows the available profiles with the current one selected',async()=>{
 const s=setup();const host=el('section');await s.context.LcScanProfile.build(host);
 const nodes=all(host);const select=nodes.find(n=>n.tag==='select');
 assert.deepEqual(Array.from(select.children,o=>o.value),['STANDARD','PROFI_SCANNER']);assert.equal(select.value,'STANDARD');
 assert.ok(nodes.some(n=>String(n.textContent).includes('Bisheriges Verhalten')&&String(n.textContent).includes('300 DPI')));
});
test('saving sends the chosen profile and reports success or the error',async()=>{
 const s=setup();const host=el('section');await s.context.LcScanProfile.build(host);
 const nodes=all(host),select=nodes.find(n=>n.tag==='select'),save=nodes.find(n=>n.tag==='button'),message=nodes.find(n=>n.attrs.role==='status');
 select.value='PROFI_SCANNER';await save.onclick();
 const put=s.calls.find(c=>c.options?.method==='PUT');assert.deepEqual(JSON.parse(put.options.body),{profile:'PROFI_SCANNER'});assert.equal(message.textContent,'Scan-Profil gespeichert.');
 const failing=setup(true);const host2=el('section');await failing.context.LcScanProfile.build(host2);
 const n2=all(host2);await n2.find(n=>n.tag==='button').onclick();assert.equal(n2.find(n=>n.attrs.role==='status').textContent,'Nicht erlaubt');
});
test('page loads the script',()=>{assert.match(fs.readFileSync(path.join(root,'index.html'),'utf8'),/scan-profile\.js/);});
