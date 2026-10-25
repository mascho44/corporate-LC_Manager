const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
function descendants(n){return[n,...n.children.flatMap(descendants)];}
function node(tag){return{tag,children:[],textContent:'',value:'',className:'',attributes:{},append(...items){this.children.push(...items);},insertBefore(item,ref){const i=ref?this.children.indexOf(ref):-1;if(i<0)this.children.push(item);else this.children.splice(i,0,item);},reportValidity(){return true;},replaceChildren(...items){this.children=items;},setAttribute(k,v){this.attributes[k]=v;}};}
async function fixture(responses){
 const calls=[],writes=[];const section=node('section');
 const context=vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),document:{createElement:node},URLSearchParams,Date,confirmAction:async()=>true,json:async (url,options)=>{calls.push(url);if(options?.method==='PUT'||options?.method==='POST'){writes.push({url,body:JSON.parse(options.body)});return {};}return responses[url.split('?')[0]];}});
 vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/platform-overview.js'),'utf8'),context);
 const refresh=await context.setupPlatformOverview(section);return {section,calls,writes,refresh};
}
test('audit shows metadata only and passes the tenant filter',async()=>{
 const f=await fixture({'/api/platform/memberships':[],'/api/platform/audit':[{occurredAt:'2026-10-09T10:00:00',tenantCode:'acme',username:'ann',action:'LC_UPDATED',entityType:'LETTER_OF_CREDIT',successful:true}]});
 await f.refresh();assert.ok(f.calls.includes('/api/platform/audit?limit=200'));
 const inputs=descendants(f.section).filter(n=>n.tag==='input');inputs.at(-1).value='acme';await descendants(f.section).find(n=>n.tag==='button'&&n.textContent==='Load audit').onclick();
 assert.ok(f.calls.some(u=>u.includes('tenant=acme')));assert.ok(descendants(f.section).some(n=>n.textContent==='LC_UPDATED'));
});

test('audit chains are verified per tenant with head hash and breaks',async()=>{
 const f=await fixture({'/api/platform/memberships':[],'/api/platform/audit':[],'/api/platform/audit/chain':[{tenantCode:'acme',ok:true,checked:10,unchained:3,headSeq:10,headHash:'f'.repeat(64)},{tenantCode:'beta',ok:false,checked:4,firstBadSeq:4,reason:'Inhalt veraendert'}]});await f.refresh();
 await descendants(f.section).find(n=>n.tag==='button'&&n.textContent==='Verify audit chains').onclick();
 const texts=descendants(f.section).map(n=>n.textContent);
 assert.ok(texts.includes('acme'));assert.ok(texts.includes('Chain intact'));assert.ok(texts.some(x=>x.includes('10 entries checked')&&x.includes('3 older entries not chained')&&x.includes('#10 '+'f'.repeat(64))));
 assert.ok(texts.includes('Chain broken'));assert.ok(texts.some(x=>x.includes('Break at no. 4')&&x.includes('Inhalt veraendert')));
});
