const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
const source=fs.readFileSync(path.join(root,'findings-overview.js'),'utf8');
function load(){const context=vm.createContext({});context.globalThis=context;vm.runInContext(source,context);return context.LcFindingsOverview;}
const lab=t=>({code:t});
const rows=[
 {code:'SIG',title:'Unterschrift',labels:lab('Unterschrift'),severity:'WARNING',decided:false},{code:'SIG',title:'Unterschrift',labels:lab('Unterschrift'),severity:'WARNING',decided:false},{code:'SIG',title:'Unterschrift',labels:lab('Unterschrift'),severity:'WARNING',decided:true},
 {code:'CNT',title:'Originale',labels:lab('Originale'),severity:'WARNING',decided:false},{code:'CNT',title:'Originale',labels:lab('Originale'),severity:'DISCREPANCY',decided:false},
 {code:'OK1',title:'Vorgelegt',labels:lab('Vorgelegt'),severity:'OK',decided:false},{code:'',title:'x',labels:lab('x'),severity:'WARNING',decided:false}];
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
test('kinds are read from the outcome label of the rule engine',()=>{
 const o=load();
 assert.equal(o.kindOf('Regel verletzt: Betrag stimmt nicht'),'Regel verletzt');
 assert.equal(o.kindOf('Manuelle fachliche Prüfung erforderlich: Unterschrift'),'Manuelle Prüfung nötig');
 assert.equal(o.kindOf('Regel nicht prüfbar: Angabe fehlt'),'Nicht prüfbar (Angaben fehlen)');
 assert.equal(o.kindOf('Packing List wurde vorgelegt.'),'Sonstige');assert.equal(o.kindOf(null),'Sonstige');
});
test('rule labels drop severity word and outcome label but keep ordinary colons',()=>{
 const o=load();
 assert.equal(o.ruleLabel('Hinweis · Manuelle fachliche Prüfung erforderlich: Rechnung unterzeichnet'),'Rechnung unterzeichnet');
 assert.equal(o.ruleLabel('Abweichung · Regel verletzt: Betrag'),'Betrag');
 assert.equal(o.ruleLabel('Originale: 1 erfasst / 1 gefordert. Hinweis'),'1 erfasst / 1 gefordert. Hinweis');
 assert.equal(o.ruleLabel(''),'');
});
test('chips can be built per dimension and filters combine',()=>{
 const o=load();
 const data=[
  {code:'R1',kind:'Manuelle Prüfung nötig',document:'a.pdf',severity:'WARNING',decided:false,labels:{code:'Regel 1',kind:'Manuelle Prüfung nötig',document:'a.pdf'}},
  {code:'R2',kind:'Manuelle Prüfung nötig',document:'a.pdf',severity:'WARNING',decided:false,labels:{code:'Regel 2',kind:'Manuelle Prüfung nötig',document:'a.pdf'}},
  {code:'R1',kind:'Manuelle Prüfung nötig',document:'b.pdf',severity:'WARNING',decided:false,labels:{code:'Regel 1',kind:'Manuelle Prüfung nötig',document:'b.pdf'}},
  {code:'R3',kind:'Regel verletzt',document:'b.pdf',severity:'DISCREPANCY',decided:false,labels:{code:'Regel 3',kind:'Regel verletzt',document:'b.pdf'}}];
 assert.deepEqual(Array.from(o.summarizeBy(data,'document'),e=>[e.key,e.open]),[['b.pdf',2],['a.pdf',2]].sort((x,y)=>x[0].localeCompare(y[0])));
 assert.deepEqual(Array.from(o.summarizeBy(data,'kind'),e=>[e.key,e.open]),[['Manuelle Prüfung nötig',3],['Regel verletzt',1]]);
 assert.equal(o.summarizeBy(data,'code')[0].label,'Regel 1');
 assert.equal(o.bulkTargets(data,{kind:'Manuelle Prüfung nötig',document:'a.pdf'}).length,2);
 assert.equal(o.bulkTargets(data,{kind:'Regel verletzt'}).length,0,'discrepancies are never bulk targets');
 assert.equal(o.bulkTargets(data,{}).length,0,'no selection, no bulk');
 assert.equal(o.matches(data[0],{kind:'',document:'a.pdf',code:''}),true);assert.equal(o.matches(data[0],{document:'b.pdf'}),false);
});
test('the filter bar stays directly in front of the list because the existing filter reads the list as its next sibling',()=>{
 assert.match(source,/bar\.before\(tools, panel\)/);assert.doesNotMatch(source,/bar\.after\(/);
 const app=fs.readFileSync(path.join(root,'app.js'),'utf8');
 assert.match(app,/toolbar=event\.target\.closest\('\.review-filter-bar'\),list=toolbar\.nextElementSibling/);
});
