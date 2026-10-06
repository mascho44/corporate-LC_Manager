const test=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const vm=require('node:vm');const path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
function fixture(){
 const nodes=new Map(),calls=[];let allowed=true,answer=async()=>({tenantName:'Synthetic tenant',memberships:[]});
 function node(tag){let id='';const n={tag,dataset:{},children:[],textContent:'',append(...items){this.children.push(...items);},replaceChildren(...items){this.children=items;},before(){},remove(){nodes.delete(id);}};Object.defineProperty(n,'id',{get:()=>id,set:value=>{id=value;nodes.set(value,n);}});return n;}
 const form=node('form');form.id='userForm';
 const context=vm.createContext({document:{createElement:node},$:selector=>nodes.get(selector.slice(1)),can:()=>allowed,json:async url=>{calls.push(url);return answer();},Error});
 vm.runInContext(source.slice(source.indexOf('/* Membership overview */')),context);
 return {context,nodes,calls,setAllowed:value=>allowed=value,setAnswer:value=>answer=value};
}
function texts(node){return[node.textContent,...node.children.flatMap(texts)].join('\n');}
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
