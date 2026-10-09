const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/audit-chain.js','utf8');
function setup(response){
 function node(tag){return{tag,children:[],textContent:'',className:'',attributes:{},append(...i){this.children.push(...i);},replaceChildren(...i){this.children=i;},after(n){this.next=n;},setAttribute(k,v){this.attributes[k]=v;},addEventListener(n,f){this[n]=f;}};}
 const toolbar=node('div');
 const context=vm.createContext({document:{querySelector:()=>toolbar,createElement:node},fetch:async()=>response});
 vm.runInContext(source,context);
 const button=toolbar.children[0],result=toolbar.next;
 const texts=()=>{const out=[];const walk=n=>{if(n.textContent)out.push(n.textContent);n.children.forEach(walk);};walk(result);return out.join('\n');};
 return{button,texts};
}
test('intact chain shows count, unchained legacy rows and head hash',async()=>{
 const f=setup({ok:true,json:async()=>({ok:true,checked:150,unchained:42,headSeq:150,headHash:'ab'.repeat(32)})});await f.button.click();
 assert.match(f.texts(),/Kette intakt: 150 Einträge geprüft, 42 ältere Einträge sind nicht verkettet/);assert.match(f.texts(),/Nr\. 150 · (ab){32}/);assert.equal(f.button.disabled,false);
});
test('broken chain names the first broken entry and reason',async()=>{
 const f=setup({ok:true,json:async()=>({ok:false,checked:57,firstBadSeq:57,reason:'Inhalt veraendert'})});await f.button.click();
 assert.match(f.texts(),/Kette gebrochen bei Nr\. 57: Inhalt veraendert/);
});
test('request errors are reported without breaking the dialog',async()=>{
 const f=setup({ok:false,status:403,json:async()=>({})});await f.button.click();
 assert.match(f.texts(),/Prüfung nicht möglich: HTTP 403/);assert.equal(f.button.disabled,false);
});
