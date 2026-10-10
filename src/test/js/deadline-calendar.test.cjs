const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
const source=fs.readFileSync(path.join(root,'deadline-calendar.js'),'utf8');
function load(){const context=vm.createContext({});context.globalThis=context;vm.runInContext(source,context);return context.LcDeadlineCalendar;}
test('month grid starts on Monday and covers whole weeks',()=>{
 const weeks=load().buildMonth(2026,9,[]);// October 2026 starts on a Thursday
 assert.equal(weeks[0][0].date,'2026-09-28');assert.equal(weeks[0][3].date,'2026-10-01');assert.equal(weeks[0][3].inMonth,true);assert.equal(weeks[0][0].inMonth,false);
 assert.ok(weeks.every(w=>w.length===7));assert.equal(weeks.at(-1)[6].date,'2026-11-01');
});
test('events land on their day and range matches the grid',()=>{
 const c=load(),weeks=c.buildMonth(2026,9,[{date:'2026-10-15',title:'a'},{date:'2026-10-15',title:'b'}]);
 const cell=weeks.flat().find(x=>x.date==='2026-10-15');assert.equal(cell.events.length,2);
 assert.deepEqual({...c.range(2026,9)},{from:'2026-09-28',to:'2026-11-01'});
});
