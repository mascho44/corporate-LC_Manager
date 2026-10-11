const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path');
const {plan,describe}=require(path.resolve(__dirname,'../../../scripts/platform-separate-admin.cjs'));
const role=(id,name='Administrator')=>({id,name,permissions:['USER_MANAGE','SETTINGS_MANAGE']});
const choices=[{id:'t1',name:'SISW Demo',code:'sisw',roles:[role('r1'),{id:'r2',name:'Editor',permissions:['LC_EDIT']}]},{id:'t2',name:'Firma',code:'firma',roles:[role('r3')]}];
const m=(o)=>({tenantId:'t1',userId:'u',username:'x',roleId:'r2',membershipActive:true,suspended:false,manageable:true,...o});
const opts={platformUser:'admin',newAdmin:'markus',tenants:['SISW Demo','Firma']};

test('new administrator is promoted or assigned first, the platform account is suspended afterwards',()=>{
 const memberships=[m({tenantId:'t1',userId:'u-markus',username:'markus',roleId:'r2'}),m({tenantId:'t1',userId:'u-admin',username:'admin',roleId:'r1'}),m({tenantId:'t2',userId:'u-admin',username:'admin',roleId:'r3'})];
 const {actions,problems}=plan(memberships,choices,opts);
 assert.deepEqual(problems,[]);
 assert.deepEqual(actions.map(a=>a.type+':'+a.tenant),['role:SISW Demo','suspend:SISW Demo','assign:Firma','suspend:Firma']);
 assert.equal(describe(actions[2]),'Firma: markus als „Administrator“ zuweisen');
});
test('nothing is planned when the separation already exists',()=>{
 const memberships=[m({tenantId:'t1',userId:'u-markus',username:'markus',roleId:'r1'}),m({tenantId:'t1',userId:'u-admin',username:'admin',roleId:'r1',suspended:true}),m({tenantId:'t2',userId:'u-markus',username:'markus',roleId:'r3'})];
 assert.deepEqual(plan(memberships,choices,opts).actions,[]);
});
test('unknown tenants, ambiguous admin roles and home identities are reported instead of guessed',()=>{
 assert.match(plan([],choices,{...opts,tenants:['Nope']}).problems[0],/nicht gefunden/);
 const two=[{id:'t1',name:'SISW Demo',code:'s',roles:[role('r1'),role('r9','Administrator 2')]}];
 assert.match(plan([],two,{...opts,tenants:['SISW Demo']}).problems[0],/genau eine Administratorrolle/);
 const home=[m({tenantId:'t1',userId:'u-markus',username:'markus',manageable:false})];
 assert.match(plan(home,choices,{...opts,tenants:['SISW Demo']}).problems[0],/Heimat-Identität/);
});
