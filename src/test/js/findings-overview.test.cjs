const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
const source=fs.readFileSync(path.join(root,'findings-overview.js'),'utf8');
function load(){const context=vm.createContext({});context.globalThis=context;vm.runInContext(source,context);return context.LcFindingsOverview;}
const rows=[
 {code:'SIG',title:'Unterschrift',severity:'WARNING',decided:false},{code:'SIG',title:'Unterschrift',severity:'WARNING',decided:false},{code:'SIG',title:'Unterschrift',severity:'WARNING',decided:true},
 {code:'CNT',title:'Originale',severity:'WARNING',decided:false},{code:'CNT',title:'Originale',severity:'DISCREPANCY',decided:false},
 {code:'OK1',title:'Vorgelegt',severity:'OK',decided:false},{code:'',title:'x',severity:'WARNING',decided:false}];
test('chips count only open findings per rule, largest first (ties by code), without finished rules',()=>{
 const s=load().summarize(rows);
 assert.deepEqual(Array.from(s,e=>[e.code,e.open,e.warnings,e.discrepancies]),[['CNT',2,1,1],['SIG',2,2,0]]);
 assert.equal(s[0].title,'Originale');assert.equal(s[1].title,'Unterschrift');
});
test('title falls back to the code and ties are ordered by code',()=>{
 const s=load().summarize([{code:'B',severity:'WARNING'},{code:'A',severity:'WARNING'}]);
 assert.deepEqual(Array.from(s,e=>e.code),['A','B']);assert.equal(s[0].title,'A');
});
test('bulk decisions cover open warnings of one rule only, never discrepancies or decided ones',()=>{
 const t=load().bulkTargets(rows,'SIG');assert.equal(t.length,2);assert.ok(t.every(r=>r.severity==='WARNING'&&!r.decided));
 assert.equal(load().bulkTargets(rows,'CNT').length,1);assert.equal(load().bulkTargets(rows,'OK1').length,0);assert.equal(load().bulkTargets(rows,'').length,0);
});
test('page loads the script',()=>{assert.match(fs.readFileSync(path.join(root,'index.html'),'utf8'),/findings-overview\.js/);});
