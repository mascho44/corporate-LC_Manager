const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/worklist.js'),'utf8');
function load(){const context=vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),});context.globalThis=context;vm.runInContext(source,context);return context.LcWorklist;}
const none={overdue:0,upcoming:0,unassigned:0,waiting:0,inbox:null,ebics:null,team:null};
test('tiles with a count above zero carry their tone, zero counts are calm',()=>{
 const t=load().tiles({...none,overdue:2,waiting:3,inbox:4,ebics:1});
 const by=Object.fromEntries(t.map(x=>[x.key,x]));
 assert.equal(by.overdue.tone,'bad');assert.equal(by.waiting.tone,'warn');assert.equal(by.inbox.tone,'info');assert.equal(by.ebics.tone,'info');
 assert.equal(by.upcoming.tone,'ok');assert.equal(by.unassigned.tone,'ok');assert.equal(by.overdue.count,2);
});
test('tiles the user cannot use are hidden instead of shown as zero',()=>{
 const keys=Array.from(load().tiles(none),x=>x.key);
 assert.deepEqual(keys,['overdue','waiting','upcoming','unassigned']);
 assert.deepEqual(Array.from(load().tiles({...none,inbox:0,ebics:0,team:0}),x=>x.key),['overdue','ebics','inbox','team','waiting','upcoming','unassigned']);
});
test('state is not conveyed by colour alone',()=>{
 const w=load();assert.equal(w.symbol('ok'),'✓');assert.equal(w.symbol('bad'),'!');assert.equal(w.symbol('warn'),'!');assert.equal(w.symbol('info'),'i');
});
test('start page loads the worklist and its styles, and the cockpit page includes the section',()=>{
 const html=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/index.html'),'utf8');
 assert.match(html,/worklist\.js/);assert.match(html,/worklist\.css/);
 const app=require('./app-source.cjs')();
 assert.match(app,/cockpit:\['cockpitSection','todaySection'/);assert.match(app,/pageSections=\['platformSection','tenantSection','membersSection','ebicsSection','slaSection','groupInboxSection','deadlineCalendarSection','profileSection','menuOverviewSection','cockpitSection','todaySection'/);
});

test('urgent files have blocking findings and expiry or shipment within five days', () => {
 const today = new Date('2026-10-15T09:00:00');
 const lcs = [
  { id: 'a', status: 'RECEIVED', expiryDate: '2026-10-18', dossierStatus: { blocking: 2 } },
  { id: 'b', status: 'RECEIVED', expiryDate: '2026-12-01', latestShipmentDate: '2026-10-16', dossierStatus: { blocking: 1 } },
  { id: 'c', status: 'RECEIVED', expiryDate: '2026-10-17', dossierStatus: { blocking: 0 } },
  { id: 'd', status: 'CLOSED', expiryDate: '2026-10-17', dossierStatus: { blocking: 3 } },
  { id: 'e', status: 'RECEIVED', expiryDate: '2026-10-25', dossierStatus: { blocking: 3 } },
  { id: 'f', status: 'RECEIVED', expiryDate: '2026-10-10', dossierStatus: { blocking: 3 } }
 ];
 assert.deepEqual(Array.from(load().urgentFiles(lcs, 5, today), x => x.id), ['b', 'a']);
});
