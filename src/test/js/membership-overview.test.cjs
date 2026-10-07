const test=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const vm=require('node:vm');const path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
function fixture(){
 const nodes=new Map(),calls=[],requests=[];let confirmation=true,allowed=true,answer=async()=>({tenantName:'Synthetic tenant',memberships:[]});
 function node(tag){let id='';const n={tag,dataset:{},children:[],textContent:'',append(...items){this.children.push(...items);},replaceChildren(...items){this.children=items;},setAttribute(key,value){this[key]=value;},before(){},remove(){nodes.delete(id);}};Object.defineProperty(n,'id',{get:()=>id,set:value=>{id=value;nodes.set(value,n);}});return n;}
 const form=node('form');form.id='userForm';
 const context=vm.createContext({document:{createElement:node},$:selector=>nodes.get(selector.slice(1)),can:()=>allowed,json:async (url,options)=>{calls.push(url);requests.push({url,options});return answer(url,options);},loadUsers:async()=>calls.push('loadUsers'),confirmAction:async()=>confirmation,encodeURIComponent,JSON,Error});
 vm.runInContext(source.slice(source.indexOf('/* Membership overview */')),context);
 return {context,nodes,calls,requests,setConfirmation:value=>confirmation=value,setAllowed:value=>allowed=value,setAnswer:value=>answer=value};
}
function texts(node){return[node.textContent,...node.children.flatMap(texts)].join('\n');}
function descendants(node){return[node,...node.children.flatMap(descendants)];}
function editableOverview(){return {tenantName:'Synthetic tenant',roleEditingEnabled:true,roleChoices:[{id:'old-role',name:'<SCRIPT>Old role</SCRIPT>'},{id:'new-role',name:'Reviewer'}],memberships:[{userId:'synthetic-id',username:'Synthetic user',roleName:'Old role',roleId:'old-role',permissions:[],active:true}]};}
function accessOverview(suspended=false,identityActive=true){return {tenantName:'Synthetic tenant',accessEditingEnabled:true,memberships:[{userId:'synthetic-id',username:'Synthetic user',roleName:'Viewer',permissions:[],active:identityActive&&!suspended,suspended,identityActive}]};}
test('suspending membership sends only tenant-local suspension and refreshes',async()=>{
 const f=fixture();f.setAnswer(async()=>accessOverview());await f.context.loadMembershipOverview();
 const button=descendants(f.nodes.get('membershipOverviewBody')).find(n=>n.tag==='button');await button.onclick();
 const request=f.requests.find(r=>r.options?.method==='PUT');assert.equal(request.url,'/api/users/memberships/synthetic-id/access');assert.deepEqual(JSON.parse(request.options.body),{suspended:true});
 assert.ok(f.calls.includes('loadUsers'));assert.match(texts(f.nodes.get('membershipOverviewBody')),/Only access to this tenant/);
});
test('activation clears suspension rather than activating the global identity',async()=>{
 const f=fixture();f.setAnswer(async()=>accessOverview(true));await f.context.loadMembershipOverview();
 const button=descendants(f.nodes.get('membershipOverviewBody')).find(n=>n.tag==='button');assert.match(button.textContent,/Activate/);await button.onclick();
 assert.deepEqual(JSON.parse(f.requests.find(r=>r.options?.method==='PUT').options.body),{suspended:false});
});
test('cancelling suspension does not send a mutation',async()=>{
 const f=fixture();f.setConfirmation(false);f.setAnswer(async()=>accessOverview());await f.context.loadMembershipOverview();
 const button=descendants(f.nodes.get('membershipOverviewBody')).find(n=>n.tag==='button');await button.onclick();assert.equal(f.requests.some(r=>r.options?.method==='PUT'),false);assert.equal(button.disabled,false);
});
test('inactive global account and own membership cannot be toggled',async()=>{
 const f=fixture();f.setAnswer(async()=>accessOverview(true,false));await f.context.loadMembershipOverview();
 let button=descendants(f.nodes.get('membershipOverviewBody')).find(n=>n.tag==='button');assert.equal(button.disabled,true);await button.onclick();assert.equal(f.requests.some(r=>r.options?.method==='PUT'),false);assert.match(texts(f.nodes.get('membershipOverviewBody')),/global account is inactive/);
 f.context.currentUser={username:'Synthetic user'};f.setAnswer(async()=>accessOverview());await f.context.loadMembershipOverview();button=descendants(f.nodes.get('membershipOverviewBody')).find(n=>n.tag==='button');assert.equal(button.disabled,true);await button.onclick();assert.equal(f.requests.some(r=>r.options?.method==='PUT'),false);
});
test('access failure remains retryable and revoked permission stops submission',async()=>{
 const f=fixture();f.setAnswer(async()=>accessOverview());await f.context.loadMembershipOverview();
 const button=descendants(f.nodes.get('membershipOverviewBody')).find(n=>n.tag==='button');f.setAnswer(async()=>{throw Error('Last administrator is protected.');});await button.onclick();assert.equal(button.disabled,false);assert.match(texts(f.nodes.get('membershipOverviewBody')),/Last administrator/);
 const count=f.requests.length;f.setAllowed(false);await button.onclick();assert.equal(f.requests.length,count);
});
test('role editor sends only roleId and refreshes after successful save',async()=>{
 const f=fixture();f.setAnswer(async()=>editableOverview());await f.context.loadMembershipOverview();
 const nodes=descendants(f.nodes.get('membershipOverviewBody')),select=nodes.find(n=>n.tag==='select'),save=nodes.find(n=>n.tag==='button');
 assert.equal(save.disabled,true);assert.equal(select.children[0].innerHTML,undefined);
 select.value='new-role';select.onchange();assert.equal(save.disabled,false);await save.onclick();
 const request=f.requests.find(r=>r.options?.method==='PUT');assert.equal(request.url,'/api/users/memberships/synthetic-id/role');assert.deepEqual(JSON.parse(request.options.body),{roleId:'new-role'});
 assert.ok(f.calls.includes('loadUsers'));
});
test('role save failure is visible and leaves selection retryable',async()=>{
 const f=fixture();f.setAnswer(async()=>editableOverview());await f.context.loadMembershipOverview();
 const nodes=descendants(f.nodes.get('membershipOverviewBody')),select=nodes.find(n=>n.tag==='select'),save=nodes.find(n=>n.tag==='button');
 f.setAnswer(async()=>{throw Error('The last administrator cannot be demoted.');});select.value='new-role';select.onchange();await save.onclick();
 assert.match(texts(f.nodes.get('membershipOverviewBody')),/last administrator/);assert.equal(select.disabled,false);assert.equal(save.disabled,false);
 assert.equal(f.calls.includes('loadUsers'),false);
});
test('duplicate role submissions are suppressed while saving',async()=>{
 const f=fixture();f.setAnswer(async()=>editableOverview());await f.context.loadMembershipOverview();
 const nodes=descendants(f.nodes.get('membershipOverviewBody')),select=nodes.find(n=>n.tag==='select'),save=nodes.find(n=>n.tag==='button');
 let resolve;f.setAnswer(()=>new Promise(r=>resolve=r));select.value='new-role';select.onchange();
 const pending=save.onclick();await save.onclick();assert.equal(f.requests.filter(r=>r.options?.method==='PUT').length,1);assert.equal(select.disabled,true);
 resolve({});await pending;assert.equal(select.disabled,false);
});
test('a stale role mutation cannot refresh a newer overview',async()=>{
 const f=fixture();f.setAnswer(async()=>editableOverview());await f.context.loadMembershipOverview();
 const nodes=descendants(f.nodes.get('membershipOverviewBody')),select=nodes.find(n=>n.tag==='select'),save=nodes.find(n=>n.tag==='button');
 let resolve;f.setAnswer(()=>new Promise(r=>resolve=r));select.value='new-role';select.onchange();const pending=save.onclick();
 f.setAnswer(async()=>({tenantName:'Latest overview',memberships:[]}));await f.context.loadMembershipOverview();
 resolve({});await pending;assert.equal(f.calls.includes('loadUsers'),false);assert.match(texts(f.nodes.get('membershipOverviewBody')),/Latest overview/);
});
test('read-only tenant and revoked permission cannot submit role changes',async()=>{
 const f=fixture();f.setAnswer(async()=>({...editableOverview(),roleEditingEnabled:false}));await f.context.loadMembershipOverview();
 assert.equal(descendants(f.nodes.get('membershipOverviewBody')).some(n=>n.tag==='select'),false);
 f.setAnswer(async()=>editableOverview());await f.context.loadMembershipOverview();const nodes=descendants(f.nodes.get('membershipOverviewBody')),select=nodes.find(n=>n.tag==='select'),save=nodes.find(n=>n.tag==='button');
 select.value='new-role';select.onchange();f.setAllowed(false);await save.onclick();assert.equal(f.requests.some(r=>r.options?.method==='PUT'),false);
});
test('tenant metadata is informational and safely rendered',async()=>{
 const f=fixture();f.setAnswer(async()=>({tenantName:'Synthetic tenant',settings:{code:'<script>code</script>',defaultLanguage:'en',bankEnabled:true,corporateEnabled:false},memberships:[]}));await f.context.loadMembershipOverview();
 const content=texts(f.nodes.get('membershipOverviewBody'));assert.match(content,/<script>code<\/script>/);assert.match(content,/Default language/);assert.match(content,/Enabled/);assert.match(content,/Disabled/);assert.match(content,/do not grant access permissions/);
});
test('membership overview is not requested without user-management rights',async()=>{
 const f=fixture();f.setAllowed(false);await f.context.loadMembershipOverview();assert.equal(f.calls.length,0);assert.equal(f.nodes.has('membershipOverview'),false);
});
test('untrusted membership names are rendered as text and English is the fallback',async()=>{
 const f=fixture();f.setAnswer(async()=>({tenantName:'<img src=x>',memberships:[{username:'<script>alert(1)</script>',roleName:'<b>Role</b>',permissions:['USER_MANAGE'],active:true}]}));await f.context.loadMembershipOverview();
 const body=f.nodes.get('membershipOverviewBody');assert.match(texts(body),/<script>alert\(1\)<\/script>/);assert.match(texts(body),/Active/);assert.equal(body.innerHTML,undefined);assert.deepEqual(f.calls,['/api/users/memberships']);
});
test('membership loading failure leaves existing user administration intact',async()=>{
 const f=fixture();f.setAnswer(async()=>{throw Error('private diagnostics');});await f.context.loadMembershipOverview();assert.match(texts(f.nodes.get('membershipOverviewBody')),/User administration remains available/);assert.doesNotMatch(texts(f.nodes.get('membershipOverviewBody')),/private diagnostics/);assert.equal(f.nodes.has('userForm'),true);
});
test('a stale membership response cannot replace the latest overview',async()=>{
 const f=fixture();let resolve;f.setAnswer(()=>new Promise(r=>resolve=r));const first=f.context.loadMembershipOverview();f.setAnswer(async()=>({tenantName:'Latest tenant',memberships:[]}));await f.context.loadMembershipOverview();resolve({tenantName:'Old tenant',memberships:[]});await first;assert.match(texts(f.nodes.get('membershipOverviewBody')),/Latest tenant/);assert.doesNotMatch(texts(f.nodes.get('membershipOverviewBody')),/Old tenant/);
});
