const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/worklist.js'),'utf8');
function load(){const context=vm.createContext({});context.globalThis=context;vm.runInContext(source,context);return context.LcWorklist;}
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
 const app=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
 assert.match(app,/cockpit:\['cockpitSection','todaySection'/);assert.match(app,/pageSections=\['platformSection','tenantSection','ebicsSection','groupInboxSection','deadlineCalendarSection','profileSection','menuOverviewSection','cockpitSection','todaySection'/);
});
