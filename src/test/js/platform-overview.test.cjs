const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
function descendants(n){return[n,...n.children.flatMap(descendants)];}
function node(tag){return{tag,children:[],textContent:'',value:'',className:'',attributes:{},append(...items){this.children.push(...items);},replaceChildren(...items){this.children=items;},setAttribute(k,v){this.attributes[k]=v;}};}
async function fixture(responses){
 const calls=[];const section=node('section');
 const context=vm.createContext({document:{createElement:node},URLSearchParams,Date,json:async url=>{calls.push(url);return responses[url.split('?')[0]];}});
 vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/platform-overview.js'),'utf8'),context);
 const refresh=await context.setupPlatformOverview(section);return {section,calls,refresh};
}
const memberships=[{username:'<b>x</b>',displayName:'Ann',accountActive:true,tenantCode:'acme',tenantName:'Acme',tenantActive:true,roleName:'Admin',membershipActive:true,suspended:false},{username:'bob',displayName:'Bob',accountActive:true,tenantCode:'beta',tenantName:'Beta',tenantActive:true,roleName:'Viewer',membershipActive:true,suspended:true}];
test('lists memberships with escaped text, filters locally and marks suspended ones',async()=>{
 const f=await fixture({'/api/platform/memberships':memberships,'/api/platform/audit':[]});await f.refresh();
 const texts=descendants(f.section).map(n=>n.textContent);assert.ok(texts.includes('<b>x</b>'));assert.ok(texts.includes('Membership suspended'));
 const input=descendants(f.section).find(n=>n.tag==='input'&&n.type==='search');input.value='bob';input.oninput();
 assert.ok(descendants(f.section).some(n=>n.textContent==='1 / 2 memberships'));
});
test('audit shows metadata only and passes the tenant filter',async()=>{
 const f=await fixture({'/api/platform/memberships':[],'/api/platform/audit':[{occurredAt:'2026-10-09T10:00:00',tenantCode:'acme',username:'ann',action:'LC_UPDATED',entityType:'LETTER_OF_CREDIT',successful:true}]});
 await f.refresh();assert.ok(f.calls.includes('/api/platform/audit?limit=200'));
 const inputs=descendants(f.section).filter(n=>n.tag==='input');inputs.at(-1).value='acme';await descendants(f.section).find(n=>n.tag==='button').onclick();
 assert.ok(f.calls.some(u=>u.includes('tenant=acme')));assert.ok(descendants(f.section).some(n=>n.textContent==='LC_UPDATED'));
});
