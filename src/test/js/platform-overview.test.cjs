const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
function descendants(n){return[n,...n.children.flatMap(descendants)];}
function node(tag){return{tag,children:[],textContent:'',value:'',className:'',attributes:{},append(...items){this.children.push(...items);},insertBefore(item,ref){const i=ref?this.children.indexOf(ref):-1;if(i<0)this.children.push(item);else this.children.splice(i,0,item);},reportValidity(){return true;},replaceChildren(...items){this.children=items;},setAttribute(k,v){this.attributes[k]=v;}};}
async function fixture(responses){
 const calls=[],writes=[];const section=node('section');
 const context=vm.createContext({document:{createElement:node},URLSearchParams,Date,confirmAction:async()=>true,json:async (url,options)=>{calls.push(url);if(options?.method==='PUT'||options?.method==='POST'){writes.push({url,body:JSON.parse(options.body)});return {};}return responses[url.split('?')[0]];}});
 vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/platform-overview.js'),'utf8'),context);
 const refresh=await context.setupPlatformOverview(section);return {section,calls,writes,refresh};
}
const memberships=[{username:'<b>x</b>',displayName:'Ann',accountActive:true,tenantCode:'acme',tenantName:'Acme',tenantActive:true,roleName:'Admin',membershipActive:true,suspended:false,tenantId:'t1',userId:'u1',roleId:'r1',manageable:true},{username:'bob',displayName:'Bob',accountActive:true,tenantCode:'beta',tenantName:'Beta',tenantActive:true,roleName:'Viewer',membershipActive:true,suspended:true,tenantId:'t2',userId:'u2',roleId:'r2',manageable:false}];
test('lists memberships with escaped text, filters locally and marks suspended ones',async()=>{
 const f=await fixture({'/api/platform/memberships':memberships,'/api/platform/audit':[]});await f.refresh();
 const texts=descendants(f.section).map(n=>n.textContent);assert.ok(texts.includes('<b>x</b>'));assert.ok(texts.includes('Membership suspended'));
 const input=descendants(f.section).find(n=>n.tag==='input'&&n.type==='search');input.value='bob';input.oninput();
 assert.ok(descendants(f.section).some(n=>n.textContent==='1 / 2 memberships'));
});
test('audit shows metadata only and passes the tenant filter',async()=>{
 const f=await fixture({'/api/platform/memberships':[],'/api/platform/audit':[{occurredAt:'2026-10-09T10:00:00',tenantCode:'acme',username:'ann',action:'LC_UPDATED',entityType:'LETTER_OF_CREDIT',successful:true}]});
 await f.refresh();assert.ok(f.calls.includes('/api/platform/audit?limit=200'));
 const inputs=descendants(f.section).filter(n=>n.tag==='input');inputs.at(-1).value='acme';await descendants(f.section).find(n=>n.tag==='button'&&n.textContent==='Load audit').onclick();
 assert.ok(f.calls.some(u=>u.includes('tenant=acme')));assert.ok(descendants(f.section).some(n=>n.textContent==='LC_UPDATED'));
});

test('manageable memberships offer role change and access toggle, home identities do not',async()=>{
 const f=await fixture({'/api/platform/memberships':memberships,'/api/platform/invitations/choices':[{id:'t1',roles:[{id:'r1',name:'Admin'},{id:'r9',name:'Viewer'}]}],'/api/platform/audit':[]});await f.refresh();
 const buttons=descendants(f.section).filter(n=>n.tag==='button');
 const change=buttons.find(b=>b.textContent==='Change role'),toggle=buttons.find(b=>b.textContent==='Suspend access');
 assert.ok(change&&toggle);assert.equal(buttons.filter(b=>b.textContent==='Change role').length,1);
 assert.ok(descendants(f.section).some(n=>n.textContent==='Managed in the home tenant'));
 const select=descendants(f.section).filter(n=>n.tag==='select').at(-1);select.value='r9';
 await change.onclick();
 assert.deepEqual(f.writes.at(-1),{url:'/api/platform/memberships/t1/u1/role',body:{roleId:'r9'}});
 await toggle.onclick();
 assert.deepEqual(f.writes.at(-1),{url:'/api/platform/memberships/t1/u1/access',body:{suspended:true}});
});

test('an existing identity can be assigned to a tenant role with confirmation',async()=>{
 const f=await fixture({'/api/platform/memberships':[],'/api/platform/invitations/choices':[{id:'t1',name:'Acme',code:'acme',roles:[{id:'r1',name:'Admin'},{id:'r9',name:'Viewer'}]}],'/api/platform/audit':[]});await f.refresh();
 const form=descendants(f.section).find(n=>n.tag==='form');
 const selects=descendants(form).filter(n=>n.tag==='select'),input=descendants(form).find(n=>n.tag==='input');
 assert.equal(selects[0].children.length,1);assert.deepEqual(selects[1].children.map(o=>o.value),['r1','r9']);
 selects[0].value='t1';selects[1].value='r9';input.value='  shared.user ';
 await form.onsubmit({preventDefault(){}});
 const write=f.writes.at(-1);assert.equal(write.url,'/api/platform/memberships');assert.deepEqual(write.body,{tenantId:'t1',username:'shared.user',roleId:'r9'});
});
