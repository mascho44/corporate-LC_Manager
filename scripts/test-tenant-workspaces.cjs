// Disposable localhost installation ONLY. Never point this synthetic smoke test at production.
// Requires a fresh database and a synthetic initial admin password as configured below.
const assert=require('node:assert/strict'),crypto=require('node:crypto');
const base='http://127.0.0.1:18086',cookies=new Map();let csrf='';
async function call(path,method='GET',body,expected=200,type='application/json'){
 const headers={Cookie:[...cookies].map(([k,v])=>`${k}=${v}`).join(';')};if(csrf)headers['X-CSRF-TOKEN']=csrf;if(body!==undefined)headers['Content-Type']=type;
 const response=await fetch(base+path,{method,headers,body:body===undefined?undefined:type==='application/json'?JSON.stringify(body):body});
 for(const cookie of response.headers.getSetCookie()){const pair=cookie.split(';')[0],at=pair.indexOf('=');cookies.set(pair.slice(0,at),pair.slice(at+1));}
 const content=await response.text();assert.equal(response.status,expected,`${method} ${path}: HTTP ${response.status}`);return content?JSON.parse(content):null;
}
function totp(secret){
 let buffer=0,bits=0;const bytes=[];for(const c of secret){buffer=(buffer<<5)|'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(c);bits+=5;if(bits>=8){bytes.push((buffer>>>(bits-8))&255);bits-=8;}}
 const counter=Buffer.alloc(8);counter.writeBigUInt64BE(BigInt(Math.floor(Date.now()/30000)));const h=crypto.createHmac('sha1',Buffer.from(bytes)).update(counter).digest(),offset=h[19]&15;return String((h.readUInt32BE(offset)&0x7fffffff)%1000000).padStart(6,'0');
}
function swift(reference){return `:20:${reference}\n:31C:261001\n:31D:271231SYNTHETIC\n:32B:EUR1000,00\n:50:SYNTHETIC APPLICANT\n:59:SYNTHETIC BENEFICIARY\n:46A:SYNTHETIC INVOICE\n:47A:SYNTHETIC TEST CONDITIONS` ;}
(async()=>{
 const setup=await call('/api/auth/login','POST',{username:'admin',password:'SyntheticOnly123!'});assert.equal(setup.requiresTotpSetup,true);
 await call('/api/auth/login/totp','POST',{code:totp(setup.secret)});csrf=(await call('/api/auth/me')).csrfToken;
 const home=(await call('/api/tenants')).selectedTenantId;
 const homeLc=await call('/api/lcs/import/mt700','POST',swift('SYNTHETIC-HOME'),200,'text/plain');
 const created=await call('/api/tenants','POST',{code:'synthetic-smoke',name:'Synthetic smoke workspace',defaultLanguage:'en',bankEnabled:true,corporateEnabled:false});
 await call('/api/tenants/'+created.id+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 assert.equal((await call('/api/tenants')).selectedTenantId,created.id);assert.deepEqual(await call('/api/lcs'),[]);
 await call('/api/lcs/'+homeLc.id,'GET',undefined,404);
 const localLc=await call('/api/lcs/import/mt700','POST',swift('SYNTHETIC-LOCAL'),200,'text/plain');assert.equal((await call('/api/lcs')).length,1);
 assert.equal((await call('/api/profile')).username,'admin');assert.equal((await call('/api/profile/language')).tenantId,created.id);
 const own=(await call('/api/users/memberships')).memberships.find(m=>m.username==='admin');assert.ok(own);
 const newRole=await call('/api/roles','POST',{name:'Synthetic limited administrator',baseRole:'ADMIN',permissions:['USER_MANAGE','LC_EDIT','SWIFT_IMPORT','AUDIT_VIEW']});
 await call('/api/users/memberships/shared/'+own.userId+'/role','PUT',{roleId:newRole.id});
 // The next request must revoke stale permissions rather than silently retaining old grants.
 await call('/api/tenants','GET',undefined,401);
 const login=await call('/api/auth/login','POST',{username:'admin',password:'SyntheticOnly123!'});assert.equal(login.requiresTotp,true);await call('/api/auth/login/totp','POST',{code:totp(setup.secret)});csrf=(await call('/api/auth/me')).csrfToken;
 await call('/api/tenants/'+created.id+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;assert.equal((await call('/api/lcs')).length,1);
 await call('/api/tenants/'+crypto.randomUUID()+'/select','POST',undefined,403);
 await call('/api/tenants/'+home+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 assert.deepEqual((await call('/api/lcs')).map(l=>l.reference),['SYNTHETIC-HOME']);await call('/api/lcs/'+localLc.id,'GET',undefined,404);
 console.log('PASS: TOTP login, native PostgreSQL tenant creation, selection, local role update, stale-session revocation, profile/language and two-way LC isolation.');
})().catch(error=>{console.error(error.message);process.exitCode=1;});
