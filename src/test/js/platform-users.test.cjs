const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
function load(){const context=vm.createContext({});context.globalThis=context;vm.runInContext(fs.readFileSync(path.join(root,'platform-users.js'),'utf8'),context);return context.LcPlatformUsers;}
const U=load();
const eq=(a,b)=>assert.equal(JSON.stringify(a),JSON.stringify(b)); // arrays from the vm context have another prototype
const T1='t-1',T2='t-2',HOME=U.DEFAULT_TENANT;
const role=(id,name,permissions=[])=>({id,name,permissions});
const choices=[{id:HOME,home:true,name:'Alt',code:'alt',roles:[role('h1','Administrator',['USER_MANAGE'])]},{id:T1,name:'SISW Demo',code:'sisw',roles:[role('a1','Administrator',['USER_MANAGE','SETTINGS_MANAGE']),role('e1','Editor',['LC_EDIT'])]},{id:T2,name:'Firma',code:'firma',roles:[role('a2','Administrator',['USER_MANAGE','SETTINGS_MANAGE']),role('v2','Viewer')]}];
const accounts=[{id:'u-markus',username:'markus',displayName:'Markus',email:'m@x.de',active:true,platformAdministrator:false,invitationPending:false},{id:'u-admin',username:'admin',displayName:'Admin',email:'a@x.de',active:true,platformAdministrator:true,invitationPending:false},{id:'u-anna',username:'anna',displayName:'Anna',email:'anna@x.de',active:true,platformAdministrator:false,invitationPending:false},{id:'u-old',username:'old',displayName:'Old',email:'o@x.de',active:false,platformAdministrator:false,invitationPending:false},{id:'u-new',username:'neu',displayName:'Neu',email:'n@x.de',active:false,platformAdministrator:false,invitationPending:true}];
const row=(o)=>({username:'markus',displayName:'Markus',accountActive:true,tenantCode:'sisw',tenantName:'SISW Demo',tenantActive:true,roleName:'Editor',membershipActive:true,suspended:false,tenantId:T1,userId:'u-markus',roleId:'e1',manageable:true,...o});
const rows=[row({}),row({tenantId:HOME,tenantName:'Alt',roleId:'h1',roleName:'Administrator',manageable:false}),row({username:'anna',userId:'u-anna',tenantId:T2,tenantName:'Firma',roleId:'v2',roleName:'Viewer',suspended:true})];

test('the user list is searchable by name, tenant and role and filterable by tenant and state',()=>{
 eq(U.userList(accounts,rows,{text:'firma'}).map(a=>a.username),['anna']);
 eq(U.userList(accounts,rows,{text:'editor'}).map(a=>a.username),['markus']);
 eq(U.userList(accounts,rows,{tenantId:T1}).map(a=>a.username),['markus']);
 eq(U.userList(accounts,rows,{state:'inactive'}).map(a=>a.username).sort(),['neu','old']);
 eq(U.userList(accounts,rows,{state:'pending'}).map(a=>a.username),['neu']);
 eq(U.userList(accounts,rows,{state:'platform'}).map(a=>a.username),['admin']);
 eq(U.userList(accounts,rows,{}).map(a=>a.username),['admin','anna','markus','neu','old']);
});
test('only tenants without a membership can be added, never the identity home',()=>{
 eq(U.addableTenants(accounts[0],choices,rows).map(c=>c.id),[T2]);
 eq(U.addableTenants(accounts[1],choices,rows).map(c=>c.id),[T1,T2]);
});
test('administrative roles are disabled for platform administrators',()=>{
 const forAdmin=U.roleOptions(accounts[1],choices[1]);assert.equal(forAdmin.find(r=>r.id==='a1').disabled,true);assert.equal(forAdmin.find(r=>r.id==='e1').disabled,false);
 assert.equal(U.roleOptions(accounts[0],choices[1]).every(r=>!r.disabled),true);
});
test('a bulk assignment plans assignments, role changes and skips with reasons',()=>{
 const adminRole=choices[2].roles[0],viewer=choices[2].roles[1];
 const plan=U.bulkPlan([accounts[0],accounts[1],accounts[2],accounts[3],accounts[4]],choices[2],adminRole,rows);
 eq(plan.assign.map(a=>a.username),['markus']);
 eq(plan.change.map(c=>c.account.username),['anna']);
 const reasons=Object.fromEntries(plan.skipped.map(s=>[s.username,s.reason]));
 assert.match(reasons.admin,/Plattformadministratoren/);assert.match(reasons.old,/nicht aktiv/);assert.match(reasons.neu,/nicht aktiv/);
 const same=U.bulkPlan([accounts[2]],choices[2],viewer,rows);assert.equal(same.skipped[0].reason,'hat diese Rolle bereits');
 const home=U.bulkPlan([accounts[0]],choices[0],choices[0].roles[0],rows);assert.match(home.skipped[0].reason,/Heimat/);
});
test('the matrix has one column per workspace tenant and a cell per user',()=>{
 const m=U.matrix(choices,accounts,rows);
 eq(m.columns.map(c=>c.name),['Firma','SISW Demo']);
 const markus=m.rows.find(r=>r.account.username==='markus');eq(markus.cells.map(c=>c&&c.roleName),[null,'Editor']);
 const anna=m.rows.find(r=>r.account.username==='anna');assert.equal(anna.cells[0].suspended,true);
});
test('membership state is described in words',()=>{
 assert.equal(U.stateOf(row({})),'aktiv');assert.equal(U.stateOf(row({suspended:true})),'Zugang ausgesetzt');assert.equal(U.stateOf(row({accountActive:false})),'Konto gesperrt');assert.equal(U.stateOf(row({tenantActive:false})),'Mandant ausgesetzt');
});
test('the page loads the module and the shell mounts it; the free-text assignment form is gone',()=>{
 const html=fs.readFileSync(path.join(root,'platform.html'),'utf8'),shell=fs.readFileSync(path.join(root,'platform-shell.js'),'utf8'),overview=fs.readFileSync(path.join(root,'platform-overview.js'),'utf8'),users=fs.readFileSync(path.join(root,'platform-users.js'),'utf8');
 assert.ok(html.includes('/platform-users.js')&&html.includes('/platform-users.css'));assert.ok(shell.includes('LcPlatformUsers'));
 assert.equal(overview.includes("'/api/platform/memberships',{method:'POST'"),false);assert.equal(users.includes('innerHTML'),false);
});
