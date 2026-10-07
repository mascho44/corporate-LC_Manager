// Disposable localhost installation ONLY. Never point this synthetic smoke test at production.
// Requires a fresh database and a synthetic initial admin password as configured below.
// The local app must have TOTP_ENCRYPTION_KEY set to a disposable value of at least 32 characters.
const assert=require('node:assert/strict'),crypto=require('node:crypto');
const base='http://127.0.0.1:18086',cookies=new Map();let csrf='';
async function call(path,method='GET',body,expected=200,type='application/json',raw=false){
 const headers={Cookie:[...cookies].map(([k,v])=>`${k}=${v}`).join(';')};if(csrf)headers['X-CSRF-TOKEN']=csrf;if(body!==undefined&&type!=='multipart')headers['Content-Type']=type;
 const response=await fetch(base+path,{method,headers,body:body===undefined?undefined:type==='application/json'?JSON.stringify(body):body});
 for(const cookie of response.headers.getSetCookie()){const pair=cookie.split(';')[0],at=pair.indexOf('=');cookies.set(pair.slice(0,at),pair.slice(at+1));}
 const content=await response.text();assert.equal(response.status,expected,`${method} ${path}: HTTP ${response.status}`);return raw?content:content?JSON.parse(content):null;
}
function attachment(text){const form=new FormData();form.append('file',new Blob([text],{type:'text/plain'}),'synthetic-annex.txt');return form;}
function totp(secret){
 let buffer=0,bits=0;const bytes=[];for(const c of secret){buffer=(buffer<<5)|'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(c);bits+=5;if(bits>=8){bytes.push((buffer>>>(bits-8))&255);bits-=8;}}
 const counter=Buffer.alloc(8);counter.writeBigUInt64BE(BigInt(Math.floor(Date.now()/30000)));const h=crypto.createHmac('sha1',Buffer.from(bytes)).update(counter).digest(),offset=h[19]&15;return String((h.readUInt32BE(offset)&0x7fffffff)%1000000).padStart(6,'0');
}
function swift(reference){return `:20:${reference}\n:31C:261001\n:31D:271231SYNTHETIC\n:32B:EUR1000,00\n:50:SYNTHETIC APPLICANT\n:59:SYNTHETIC BENEFICIARY\n:46A:SYNTHETIC INVOICE\n:47A:SYNTHETIC TEST CONDITIONS` ;}
(async()=>{
 const setup=await call('/api/auth/login','POST',{username:'admin',password:'SyntheticOnly123!'});assert.equal(setup.requiresTotpSetup,true);
 await call('/api/auth/login/totp','POST',{code:totp(setup.secret)});csrf=(await call('/api/auth/me')).csrfToken;
 const home=(await call('/api/tenants')).selectedTenantId;
 const homeCompany=await call('/api/companies','POST',{legalName:'SYNTHETIC HOME COMPANY'});
 const homeCompanies=(await call('/api/companies')).map(c=>({id:c.id,legalName:c.legalName}));
 const homeLc=await call('/api/lcs/import/mt700','POST',swift('SYNTHETIC-HOME'),200,'text/plain');
 const homeContent='SYNTHETIC HOME ATTACHMENT ONLY';
 const homeDocument=await call('/api/lcs/'+homeLc.id+'/documents?type=ANNEX','POST',attachment(homeContent),200,'multipart');
 assert.equal(await call('/api/documents/'+homeDocument.id+'/content','GET',undefined,200,'application/json',true),homeContent);
 const created=await call('/api/tenants','POST',{code:'synthetic-smoke',name:'Synthetic smoke workspace',defaultLanguage:'en',bankEnabled:true,corporateEnabled:false});
 await call('/api/tenants/'+created.id+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 assert.equal((await call('/api/tenants')).selectedTenantId,created.id);assert.deepEqual(await call('/api/lcs'),[]);
 const initialRoles=await call('/api/roles');assert.deepEqual(initialRoles.map(r=>r.baseRole).sort(),['ADMIN','EDITOR','VIEWER']);assert.equal(initialRoles.find(r=>r.baseRole==='VIEWER').permissions.length,0);
 const readiness=await call('/api/tenants/current/readiness');assert.equal(readiness.tenantId,created.id);assert.equal(readiness.companies,0);assert.equal(readiness.templates,0);assert.equal(readiness.activeMembers,1);
 assert.deepEqual(await call('/api/companies'),[]);assert.deepEqual(await call('/api/companies/choices'),[]);
 // Company lookup currently reports unavailable IDs as 400, unlike LC lookup (404).
 await call('/api/companies/'+homeCompany.id,'GET',undefined,400);
 await call('/api/companies/'+homeCompany.id,'PUT',{legalName:'FORBIDDEN OVERWRITE'},400);
 const localCompany=await call('/api/companies','POST',{legalName:'SYNTHETIC LOCAL COMPANY'});
 assert.equal((await call('/api/tenants/current/readiness')).companies,1);
 await call('/api/tenants/current/settings','PUT',{name:'Synthetic renamed workspace',defaultLanguage:'de'});assert.equal((await call('/api/tenants/current/settings')).name,'Synthetic renamed workspace');
 await call('/api/lcs/'+homeLc.id,'GET',undefined,404);
 await call('/api/documents/'+homeDocument.id+'/content','GET',undefined,404);
 await call('/api/documents/'+homeDocument.id+'/preview','GET',undefined,404);
 await call('/api/lcs/'+homeLc.id+'/documents/'+homeDocument.id,'DELETE',undefined,404);
 const localLc=await call('/api/lcs/import/mt700','POST',swift('SYNTHETIC-LOCAL'),200,'text/plain');assert.equal((await call('/api/lcs')).length,1);
 const localContent='SYNTHETIC LOCAL ATTACHMENT ONLY';
 const localDocument=await call('/api/lcs/'+localLc.id+'/documents?type=ANNEX','POST',attachment(localContent),200,'multipart');
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
 await call('/api/documents/'+localDocument.id+'/content','GET',undefined,404);
 await call('/api/documents/'+localDocument.id+'/preview','GET',undefined,404);
 await call('/api/lcs/'+localLc.id+'/documents/'+localDocument.id,'DELETE',undefined,404);
 assert.equal(await call('/api/documents/'+homeDocument.id+'/content','GET',undefined,200,'application/json',true),homeContent);
 assert.deepEqual((await call('/api/lcs/'+homeLc.id+'/documents')).map(d=>d.id),[homeDocument.id]);
 // The fresh home workspace also contains the legacy empty bootstrap company.
 assert.deepEqual((await call('/api/companies')).map(c=>({id:c.id,legalName:c.legalName})),homeCompanies);
 assert.equal((await call('/api/companies/'+homeCompany.id)).legalName,'SYNTHETIC HOME COMPANY');
 await call('/api/companies/'+localCompany.id,'GET',undefined,400);
 await call('/api/companies/'+localCompany.id,'PUT',{legalName:'FORBIDDEN HOME OVERWRITE'},400);
 const homeViewer=(await call('/api/roles')).find(r=>r.baseRole==='VIEWER');assert.ok(homeViewer);
 const isolated=await call('/api/users','POST',{username:'synthetic-local-only',displayName:'Synthetic local identity',email:'synthetic-local-only@example.invalid',password:'SyntheticOnly456!',roleId:homeViewer.id,active:true});
 await call('/api/tenants/'+created.id+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 const viewer=await call('/api/roles','POST',{name:'Synthetic local viewer',baseRole:'VIEWER',permissions:[]});await call('/api/users/memberships/shared','POST',{username:isolated.username,roleId:viewer.id});
 await call('/api/tenants/'+home+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;await call('/api/users/memberships/'+isolated.id+'/access','PUT',{suspended:true});
 await call('/api/auth/logout','POST',undefined,204);csrf='';
 await call('/api/auth/login','POST',{username:isolated.username,password:'SyntheticOnly456!'},401);
 await call('/api/auth/login','POST',{username:isolated.username,password:'SyntheticOnly456!',tenantCode:'synthetic-smoke'});csrf=(await call('/api/auth/me')).csrfToken;
 assert.equal((await call('/api/tenants')).selectedTenantId,created.id);assert.deepEqual((await call('/api/lcs')).map(l=>l.reference),['SYNTHETIC-LOCAL']);await call('/api/lcs/'+homeLc.id,'GET',undefined,404);
 assert.equal((await call('/api/tenants')).creationEnabled,false);assert.equal((await call('/api/tenants/current/settings')).editingEnabled,false);
 await call('/api/companies','POST',{legalName:'FORBIDDEN VIEWER COMPANY'},403);
 await call('/api/companies/'+localCompany.id,'PUT',{legalName:'FORBIDDEN VIEWER OVERWRITE'},403);
 assert.deepEqual(await call('/api/companies/choices'),[{id:localCompany.id,name:'SYNTHETIC LOCAL COMPANY'}]);
 await call('/api/tenants/current/settings','PUT',{name:'Forbidden',defaultLanguage:'en'},403);
 await call('/api/tenants/current/readiness','GET',undefined,403);
 await call('/api/roles','POST',{name:'Forbidden administrator',baseRole:'ADMIN',permissions:['USER_MANAGE']},403);
 await call('/api/tenants','POST',{code:'forbidden-tenant',name:'Forbidden',defaultLanguage:'en',bankEnabled:true,corporateEnabled:false},403);
 await call('/api/lcs/import/mt700','POST',swift('SYNTHETIC-FORBIDDEN'),403,'text/plain');
 await call('/api/lcs/'+localLc.id,'DELETE',undefined,403);
 await call('/api/lcs/'+localLc.id+'/documents/'+localDocument.id,'DELETE',undefined,403);
 await call('/api/lcs/'+localLc.id+'/documents?type=ANNEX','POST',attachment('FORBIDDEN VIEWER ATTACHMENT'),403,'multipart');
 assert.equal(await call('/api/documents/'+localDocument.id+'/content','GET',undefined,200,'application/json',true),localContent);
 assert.deepEqual((await call('/api/lcs/'+localLc.id+'/documents')).map(d=>d.id),[localDocument.id]);
 await call('/api/tenants/'+home+'/select','POST',undefined,403);
 assert.equal((await call('/api/tenants')).selectedTenantId,created.id);assert.deepEqual((await call('/api/lcs')).map(l=>l.reference),['SYNTHETIC-LOCAL']);assert.equal((await call('/api/tenants/current/settings')).name,'Synthetic renamed workspace');
 // Local membership grants never imply platform authority.
 assert.equal((await call('/api/platform/access')).enabled,false);
 await call('/api/platform/users','GET',undefined,403);
 await call('/api/platform/users/'+isolated.id+'/access','PUT',{active:false},403);
 const viewerSession=[...cookies],viewerCsrf=csrf;cookies.clear();csrf='';
 const platformLogin=await call('/api/auth/login','POST',{username:'admin',password:'SyntheticOnly123!',tenantCode:'synthetic-smoke'});assert.equal(platformLogin.requiresTotp,true);
 await call('/api/auth/login/totp','POST',{code:totp(setup.secret)});csrf=(await call('/api/auth/me')).csrfToken;
 assert.equal((await call('/api/platform/access')).enabled,true);
 const globalAccounts=await call('/api/platform/users');const platformAdmin=globalAccounts.find(a=>a.username==='admin');assert.equal(platformAdmin.platformAdministrator,true);
 assert.ok(globalAccounts.every(a=>!('passwordHash' in a)&&!('totpSecretEncrypted' in a)&&!('recoveryCodeHashes' in a)));
 await call('/api/platform/users/'+platformAdmin.id+'/access','PUT',{active:false},400);
 await call('/api/platform/users/'+isolated.id+'/access','PUT',{active:false});
 const adminSession=[...cookies],adminCsrf=csrf;cookies.clear();viewerSession.forEach(([k,v])=>cookies.set(k,v));csrf=viewerCsrf;
 await call('/api/tenants','GET',undefined,401); // Revoke the already-open viewer session.
 cookies.clear();adminSession.forEach(([k,v])=>cookies.set(k,v));csrf=adminCsrf;
 await call('/api/platform/users/'+isolated.id+'/access','PUT',{active:true});
 cookies.clear();csrf='';
 await call('/api/auth/login','POST',{username:isolated.username,password:'SyntheticOnly456!'},401); // Global activation must not lift the local home suspension.
 await call('/api/auth/login','POST',{username:isolated.username,password:'SyntheticOnly456!',tenantCode:'synthetic-smoke'});csrf=(await call('/api/auth/me')).csrfToken;
 assert.equal((await call('/api/tenants')).selectedTenantId,created.id);
 console.log('PASS: TOTP login, PostgreSQL tenant provisioning and default roles, empty setup counts, settings, stale-session revocation, two-way LC, company and attachment isolation, explicit tenant login with suspended default access, viewer write denial, failed-switch session preservation and global platform access/suspension with stale-session revocation.');
})().catch(error=>{console.error(error.message);process.exitCode=1;});
