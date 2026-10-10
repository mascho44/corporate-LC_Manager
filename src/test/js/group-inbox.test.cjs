const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
const source=fs.readFileSync(path.join(root,'group-inbox.js'),'utf8');
function load(){const context=vm.createContext({});context.globalThis=context;vm.runInContext(source,context);return context.LcGroupInbox;}
test('items are split into claimable, own and colleagues\' tasks, case-insensitively',()=>{
 const g=load().group([{taskId:1,assignedTo:null},{taskId:2,assignedTo:'Anna'},{taskId:3,assignedTo:'ben'},{taskId:4}],'anna');
 assert.deepEqual(Array.from(g.open,i=>i.taskId),[1,4]);assert.deepEqual(Array.from(g.mine,i=>i.taskId),[2]);assert.deepEqual(Array.from(g.others,i=>i.taskId),[3]);
});
test('missing input gives empty groups',()=>{
 const g=load().group(null,undefined);assert.equal(g.open.length+g.mine.length+g.others.length,0);
});
test('page loads the script and the navigation knows the section',()=>{
 assert.match(fs.readFileSync(path.join(root,'index.html'),'utf8'),/group-inbox\.js/);
 const app=fs.readFileSync(path.join(root,'app.js'),'utf8');
 assert.match(app,/groupInbox:\['groupInboxSection'\]/);assert.match(app,/'ebicsSection','groupInboxSection'/);
});
