const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/audit-search.js','utf8');
function setup(responses){
 function node(tag){return{tag,children:[],textContent:'',value:'',className:'',attributes:{},append(...i){this.children.push(...i);},after(n){this.next=n;},setAttribute(k,v){this.attributes[k]=v;},addEventListener(n,f){this[n]=f;}};}
 const toolbar=node('div'),auditBtn=node('button'),calls=[],navigations=[];let filtered=0;
 const context=vm.createContext({URLSearchParams,Date,Object,document:{querySelector:s=>s==='#auditBtn'?auditBtn:toolbar,createElement:node},location:{assign:u=>navigations.push(u)},
  fetch:async url=>{calls.push(url);const r=responses.shift();return r;},filterAudit:()=>{filtered++;}});
 vm.runInContext(source,context);
 const [from,to,user,load,exportButton]=toolbar.children;
 return{from,to,user,load,exportButton,status:toolbar.next,auditBtn,calls,navigations,context,get filtered(){return filtered;}};
}
test('loads the filtered range from the server and refreshes the list',async()=>{
 const f=setup([{ok:true,json:async()=>[{action:'LC_UPDATED'}]}]);
 f.from.value='2026-10-01';f.to.value='2026-10-09';f.user.value=' markus ';await f.load.click();
 assert.equal(f.calls[0],'/api/audit?from=2026-10-01&to=2026-10-09&user=markus&limit=2000');
 assert.equal(f.context.auditEvents.length,1);assert.equal(f.filtered,1);assert.equal(f.load.disabled,false);
});
test('export keeps the same filters and plain export has none',()=>{
 const f=setup([]);f.from.value='2026-10-01';f.exportButton.click();
 assert.deepEqual(f.navigations,['/api/audit/export.csv?from=2026-10-01']);f.from.value='';f.exportButton.click();assert.equal(f.navigations[1],'/api/audit/export.csv');
});
test('errors from the server are shown and retention information is rendered',async()=>{
 const f=setup([{ok:false,status:400,json:async()=>({error:'Der Beginn des Zeitraums liegt nach dem Ende.'})},{ok:true,json:async()=>({retentionYears:10,oldestEvent:'2026-09-22T10:00:00',eventsBeyondRetention:0})}]);
 await f.load.click();assert.match(f.status.textContent,/Beginn des Zeitraums liegt nach dem Ende/);
 await f.auditBtn.click();assert.match(f.status.textContent,/Aufbewahrung: 10 Jahre/);assert.match(f.status.textContent,/0 Einträge außerhalb der Frist/);assert.match(f.status.textContent,/keine automatische Löschung/);
});
