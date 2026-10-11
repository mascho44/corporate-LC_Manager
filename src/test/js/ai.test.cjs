const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
const source=fs.readFileSync(path.join(root,'ai.js'),'utf8');
function load(){const context=vm.createContext({});context.globalThis=context;vm.runInContext(source,context);return context.LcAi;}
test('hints are sorted by severity and unknown severities count as low',()=>{
 const sorted=load().sortHints([{severity:'LOW',check:'c'},{severity:'WEIRD',check:'w'},{severity:'HIGH',check:'a'},{severity:'MEDIUM',check:'b'}]);
 assert.deepEqual(Array.from(sorted,h=>h.check),['a','b','c','w']);
});
test('the hints button is hidden by default and the start page loads the script and styles',()=>{
 const index=fs.readFileSync(path.join(root,'index.html'),'utf8'),app=fs.readFileSync(path.join(root,'app.js'),'utf8');
 assert.ok(index.includes('/ai.js')&&index.includes('/ai.css'));assert.ok(app.includes('data-ai-hints hidden'));
});
test('the settings line says whose configuration is in effect',()=>{
 const {describeSource}=load();
 assert.match(describeSource({source:'mandant',provider:'mistral',model:'m'}),/eigener Dienst \(mistral, Modell m\)/);
 assert.match(describeSource({source:'betreiber',provider:'ollama',model:'q'}),/Standard des Betreibers/);
 assert.match(describeSource({source:null,available:true}),/Noch kein KI-Dienst/);
 assert.match(describeSource({source:null,available:false}),/noch keine KI-Dienste freigegeben/);
});
test('the settings box never renders the key and uses text content only',()=>{
 const code=fs.readFileSync(path.join(root,'ai.js'),'utf8');
 assert.equal(code.includes('innerHTML'),false);
 assert.ok(code.includes("key.type = 'password'"));assert.ok(code.includes("autocomplete = 'new-password'"));
});
