const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
const source=fs.readFileSync(path.join(root,'amendment-status.js'),'utf8');
function load(){const context=vm.createContext({});context.globalThis=context;vm.runInContext(source,context);return context.LcAmendmentStatus;}
const pending={id:'a1',status:'PENDING',amendmentNumber:'2',newExpiryDate:'2027-12-31',newExpiryPlace:'BERLIN',amountIncrease:'50',changedDocuments:'+X',otherChanges:'{"59":"NEW","44E":"NINGBO"}'};
test('open amendments list what would change and offer accept and reject to editors only',()=>{
 const s=load();
 const lines=Array.from(s.plannedChanges(pending));
 assert.ok(lines.includes('Ablauf: 31.12.2027 BERLIN'));assert.ok(lines.includes('Betrag erhöht um 50,00'));assert.ok(lines.includes('Dokumentenanforderungen (46B) geändert'));assert.ok(lines.includes('Begünstigter (59) geändert'));assert.ok(lines.includes('Feld 44E geändert'));
 const html=s.pendingHtml(pending,true);assert.match(html,/data-amendment-accept="a1"/);assert.match(html,/data-amendment-reject="a1"/);
 assert.equal(s.pendingHtml(pending,false).includes('data-amendment-accept'),false);
 assert.equal(s.pendingHtml({...pending,status:'ACCEPTED'},true),'');
});
test('status badge shows state, decider and comment in text, not only colour',()=>{
 const s=load();
 assert.match(s.badgeHtml(pending),/Offen – noch nicht wirksam/);
 const accepted=s.badgeHtml({status:'ACCEPTED',decidedBy:'anna',decidedAt:'2026-10-11T09:30:00',decisionComment:'abgestimmt'});
 assert.match(accepted,/Angenommen – wirksam/);assert.match(accepted,/anna, 2026-10-11 09:30/);assert.match(accepted,/„abgestimmt“/);
 assert.match(s.badgeHtml({status:'REJECTED'}),/Abgelehnt – ohne Wirkung/);
});
test('all dynamic text is escaped and broken extra changes do not break the list',()=>{
 const s=load();
 const html=s.badgeHtml({status:'ACCEPTED',decidedBy:'<img src=x>',decisionComment:'x"><i>y'});
 assert.equal(html.includes('<img'),false);assert.equal(html.includes('x"><i>y'),false);assert.match(html,/x&quot;&gt;&lt;i&gt;y/);
 assert.doesNotThrow(()=>s.plannedChanges({status:'PENDING',otherChanges:'not json'}));assert.equal(s.plannedChanges({status:'PENDING'}).length,0);
});
test('page loads the script',()=>{assert.match(fs.readFileSync(path.join(root,'index.html'),'utf8'),/amendment-status\.js/);});
