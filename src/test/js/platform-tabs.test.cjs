const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/platform-tabs.js'),'utf8');
const context=vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),globalThis:{}});context.globalThis=context;vm.runInContext(source,context);
const tabs=context.LcPlatformTabs;
test('summary counts tenants, accounts and invitations and flags problems',()=>{
 const result=tabs.summarize(
  [{code:'a',active:true,archived:false,administrators:1},{code:'b',active:true,archived:false,administrators:0},{code:'c',active:false,archived:false,administrators:1},{code:'d',active:false,archived:true,administrators:0}],
  [{username:'u1',active:true,totpEnabled:true,platformAdministrator:true},{username:'u2',active:true,totpEnabled:false},{username:'u3',active:false,totpEnabled:true},{username:'u4',active:false,invitationPending:true}],
  [{username:'u4',status:'MAIL_FAILED'},{username:'u5',status:'SENT'}]);
 assert.deepEqual(Array.from(result.tiles,t=>t.value),[2,2,2,1]);
 assert.equal(result.hints.length,4);
 assert.ok(result.hints.some(h=>h.tab==='tenants'&&h.text.includes('b')));
 assert.ok(result.hints.some(h=>h.text.includes('u2')));
 assert.ok(result.hints.some(h=>h.text.includes('u4')));
});
test('no findings gives no hints',()=>{assert.equal(tabs.summarize([],[],[]).hints.length,0);});
test('blocks of the console are routed to their tab',()=>{
 const el=(tag,{id='',heading='',cls=[]}={})=>({tagName:tag,id,classList:{contains:c=>cls.includes(c)},querySelector:()=>heading?{textContent:heading}:null});
 assert.equal(tabs.paneOf(el('FORM',{id:'platformPasswordForm',heading:'Eigenes Passwort'}),'accounts'),'account');
 assert.equal(tabs.paneOf(el('SECTION',{id:'platformDenials'}),'accounts'),'log');
 assert.equal(tabs.paneOf(el('SECTION',{id:'platformUsersSection'}),'tenants'),'accounts');
 assert.equal(tabs.paneOf(el('FORM',{heading:'Tenant administration',cls:['form-grid']}),'accounts'),'tenants');
 assert.equal(tabs.paneOf(el('SECTION',{heading:'Platform audit (metadata only)'}),'tenants'),'log');
 assert.equal(tabs.paneOf(el('DIV'),'tenants'),'tenants');
});
