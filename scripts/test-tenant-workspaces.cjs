// Disposable localhost installation ONLY. Never point this synthetic smoke test at production.
// Requires a fresh database and a synthetic initial admin password as configured below.
// The local app must have TOTP_ENCRYPTION_KEY set to a disposable value of at least 32 characters.
const assert=require('node:assert/strict'),crypto=require('node:crypto');
const base='http://127.0.0.1:18086',cookies=new Map();let csrf='';
let syntheticSmtp;
async function call(path,method='GET',body,expected=200,type='application/json',raw=false){
 const headers={Cookie:[...cookies].map(([k,v])=>`${k}=${v}`).join(';')};if(csrf)headers['X-CSRF-TOKEN']=csrf;if(body!==undefined&&type!=='multipart')headers['Content-Type']=type;
 const response=await fetch(base+path,{method,headers,body:body===undefined?undefined:type==='application/json'?JSON.stringify(body):body});
 for(const cookie of response.headers.getSetCookie()){const pair=cookie.split(';')[0],at=pair.indexOf('=');cookies.set(pair.slice(0,at),pair.slice(at+1));}
 const content=await response.text();assert.equal(response.status,expected,`${method} ${path}: HTTP ${response.status}`);return raw?content:content?JSON.parse(content):null;
}
function attachment(text){const form=new FormData();form.append('file',new Blob([text],{type:'text/plain'}),'synthetic-annex.txt');return form;}
async function assertPng(path,expectedPages=null){
 const response=await fetch(base+path,{headers:{Cookie:[...cookies].map(([k,v])=>`${k}=${v}`).join(';')}});
 assert.equal(response.status,200,`PNG ${path}: HTTP ${response.status}`);assert.match(response.headers.get('Content-Type'),/^image\/png/);assert.equal(response.headers.get('Cache-Control'),'no-store');
 const bytes=Buffer.from(await response.arrayBuffer());assert.ok(bytes.length>24);assert.deepEqual([...bytes.subarray(0,8)],[137,80,78,71,13,10,26,10]);
 const width=bytes.readUInt32BE(16),height=bytes.readUInt32BE(20);assert.ok(width>0&&height>0&&Math.max(width,height)<=1400);
 if(expectedPages!=null)assert.equal(Number(response.headers.get('X-Page-Count')),expectedPages);
}
async function waitTraining(id){
 const deadline=Date.now()+30000;
 while(Date.now()<deadline){const job=await call('/api/training/document-types/jobs/'+id);if(job.state==='COMPLETED'){assert.ok(job.result);return job.result;}assert.notEqual(job.state,'FAILED',job.message);await new Promise(r=>setTimeout(r,200));}
 throw Error('Synthetic background training did not finish within 30 seconds.');
}
function syntheticPdf(pages){
 const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Count '+pages.length+' /Kids ['+pages.map((_,i)=>(4+i*2)+' 0 R').join(' ')+'] >>','<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'];
 pages.forEach((text,i)=>{const stream='BT /F1 12 Tf 30 700 Td ('+text+') Tj ET';objects.push('<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents '+(5+i*2)+' 0 R >>','<< /Length '+Buffer.byteLength(stream)+' >>\nstream\n'+stream+'\nendstream');});let pdf='%PDF-1.4\n',offsets=[0];objects.forEach((object,i)=>{offsets.push(Buffer.byteLength(pdf));pdf+=(i+1)+' 0 obj\n'+object+'\nendobj\n';});const xref=Buffer.byteLength(pdf);pdf+='xref\n0 '+(objects.length+1)+'\n0000000000 65535 f \n'+offsets.slice(1).map(at=>String(at).padStart(10,'0')+' 00000 n \n').join('')+'trailer\n<< /Size '+(objects.length+1)+' /Root 1 0 R >>\nstartxref\n'+xref+'\n%%EOF\n';return Buffer.from(pdf);
}
async function waitInbox(predicate){const deadline=Date.now()+30000;while(Date.now()<deadline){const items=await call('/api/inbox');if(predicate(items))return items;await new Promise(r=>setTimeout(r,200));}const state=(await call('/api/inbox')).map(i=>({extraction:i.extractionStatus,child:!!i.sourceInboxId,type:i.classification?.suggestedType}));throw Error('Synthetic inbox processing did not finish in time: '+JSON.stringify(state));}
function totp(secret){
 let buffer=0,bits=0;const bytes=[];for(const c of secret){buffer=(buffer<<5)|'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(c);bits+=5;if(bits>=8){bytes.push((buffer>>>(bits-8))&255);bits-=8;}}
 const counter=Buffer.alloc(8);counter.writeBigUInt64BE(BigInt(Math.floor(Date.now()/30000)));const h=crypto.createHmac('sha1',Buffer.from(bytes)).update(counter).digest(),offset=h[19]&15;return String((h.readUInt32BE(offset)&0x7fffffff)%1000000).padStart(6,'0');
}
function swift(reference){return `:20:${reference}\n:31C:261001\n:31D:271231SYNTHETIC\n:32B:EUR1000,00\n:50:SYNTHETIC APPLICANT\n:59:SYNTHETIC BENEFICIARY\n:46A:SYNTHETIC INVOICE\n:47A:SYNTHETIC TEST CONDITIONS` ;}
(async()=>{
 syntheticSmtp=await require('./synthetic-smtp.cjs').startSyntheticSmtp();
 const setup=await call('/api/auth/login','POST',{username:'admin',password:'SyntheticOnly123!'});assert.equal(setup.requiresTotpSetup,true);
 await call('/api/auth/login/totp','POST',{code:totp(setup.secret)});csrf=(await call('/api/auth/me')).csrfToken;
 const home=(await call('/api/tenants')).selectedTenantId;
 const homeCompany=await call('/api/companies','POST',{legalName:'SYNTHETIC HOME COMPANY'});
 const homeCompanies=(await call('/api/companies')).map(c=>({id:c.id,legalName:c.legalName}));
 const homeLc=await call('/api/lcs/import/mt700','POST',swift('SYNTHETIC-HOME'),200,'text/plain');
 const homeContent='SYNTHETIC HOME ATTACHMENT ONLY';
 const homeDocument=await call('/api/lcs/'+homeLc.id+'/documents?type=ANNEX','POST',attachment(homeContent),200,'multipart');
 assert.equal(await call('/api/documents/'+homeDocument.id+'/content','GET',undefined,200,'application/json',true),homeContent);
 const created=await call('/api/platform/tenants','POST',{code:'synthetic-smoke',name:'Synthetic smoke workspace',defaultLanguage:'en',bankEnabled:true,corporateEnabled:true},201);
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
 await call('/api/users','POST',{username:'synthetic-retired',displayName:'Synthetic retired',email:'retired@example.invalid',password:'SyntheticOnly456!',roleId:homeViewer.id,active:true},403);
 const isolated=await call('/api/platform/users','POST',{username:'synthetic-local-only',displayName:'Synthetic local identity',email:'synthetic-local-only@example.invalid',password:'SyntheticOnly456!'},201);
 await call('/api/tenants/'+created.id+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 const viewer=await call('/api/roles','POST',{name:'Synthetic local viewer',baseRole:'VIEWER',permissions:[]});await call('/api/users/memberships/shared','POST',{username:isolated.username,roleId:viewer.id});
 await call('/api/tenants/'+home+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;await call('/api/users/memberships/'+isolated.id+'/access','PUT',{suspended:true});
 await call('/api/auth/logout','POST',undefined,204);csrf='';
 await call('/api/auth/login','POST',{username:isolated.username,password:'SyntheticOnly456!'});
 assert.equal((await call('/api/tenants')).selectedTenantId,created.id);
 assert.equal((await call('/api/tenants')).workspaces.some(t=>t.id===home),false);
 await call('/api/auth/login','POST',{username:isolated.username,password:'SyntheticOnly456!',tenantCode:'default'},401);
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
 await call('/api/platform/users','POST',{username:'synthetic-denied',displayName:'Synthetic denied',email:'denied@example.invalid',password:'SyntheticOnly789!'},403);
 await call('/api/platform/users/'+isolated.id+'/access','PUT',{active:false},403);
 const viewerSession=[...cookies],viewerCsrf=csrf;cookies.clear();csrf='';
 const platformLogin=await call('/api/auth/login','POST',{username:'admin',password:'SyntheticOnly123!',tenantCode:'synthetic-smoke'});assert.equal(platformLogin.requiresTotp,true);
 await call('/api/auth/login/totp','POST',{code:totp(setup.secret)});csrf=(await call('/api/auth/me')).csrfToken;
 assert.equal((await call('/api/platform/access')).enabled,true);
 const globalAccounts=await call('/api/platform/users');const platformAdmin=globalAccounts.find(a=>a.username==='admin');assert.equal(platformAdmin.platformAdministrator,true);
 assert.ok(globalAccounts.every(a=>!('passwordHash' in a)&&!('totpSecretEncrypted' in a)&&!('recoveryCodeHashes' in a)));
 // Real synthetic SMTP, single-use/replaced/revoked links and explicit target membership.
 const invitationChoices=await call('/api/platform/invitations/choices');assert.ok(invitationChoices.some(c=>c.id===created.id&&c.roles.some(r=>r.id===viewer.id)));
 const invitePayload={username:'synthetic-email-invite',displayName:'Synthetic email invite',email:'invite@example.invalid',tenantId:created.id,roleId:viewer.id};
 await call('/api/platform/invitations','POST',{...invitePayload,roleId:homeViewer.id},404);
 const invited=await call('/api/platform/invitations','POST',invitePayload,201);assert.ok(!('token' in invited)&&!('tokenHash' in invited));
 const replacedToken=await syntheticSmtp.tokenFor(invitePayload.email);let pendingInvitations=await call('/api/platform/invitations');assert.ok(['PENDING_MAIL','SENT'].includes(pendingInvitations.find(i=>i.userId===invited.userId).status));
 await call('/api/platform/users/'+invited.userId+'/access','PUT',{active:true},400);await call('/api/platform/users/'+invited.userId+'/platform-grant','PUT',{granted:true},400);
 await call('/api/platform/invitations/'+invited.userId+'/resend','POST');const revokedToken=await syntheticSmtp.tokenFor(invitePayload.email,replacedToken);assert.notEqual(replacedToken,revokedToken);
 await call('/api/platform/invitations/'+invited.userId,'DELETE',undefined,204);
 await call('/api/platform/invitations','POST',invitePayload,201);const acceptedToken=await syntheticSmtp.tokenFor(invitePayload.email,revokedToken);assert.notEqual(acceptedToken,revokedToken);
 const invitationAdminSession=[...cookies],invitationAdminCsrf=csrf;cookies.clear();csrf='';
 await call('/api/auth/invitation/accept','POST',{token:replacedToken,password:'SyntheticInvited123!'},400);
 await call('/api/auth/invitation/accept','POST',{token:revokedToken,password:'SyntheticInvited123!'},400);
 await call('/api/auth/login','POST',{username:invitePayload.username,password:'SyntheticInvited123!',tenantCode:'synthetic-smoke'},401);
 await call('/api/auth/invitation/accept','POST',{token:acceptedToken,password:'SyntheticInvited123!'});
 await call('/api/auth/invitation/accept','POST',{token:acceptedToken,password:'SyntheticInvited123!'},400);
 await call('/api/auth/login','POST',{username:invitePayload.username,password:'SyntheticInvited123!'});
 assert.equal((await call('/api/tenants')).selectedTenantId,created.id);
 await call('/api/auth/login','POST',{username:invitePayload.username,password:'SyntheticInvited123!',tenantCode:'synthetic-smoke'});csrf=(await call('/api/auth/me')).csrfToken;
 assert.equal((await call('/api/platform/access')).enabled,false);await call('/api/companies','POST',{legalName:'FORBIDDEN INVITED WRITE'},403);
 // Grant only after verified TOTP enrollment; revoke must take effect for an already-open session.
 const invitedSetup=await call('/api/auth/totp/setup','POST');await call('/api/auth/totp/enable','POST',{code:totp(invitedSetup.secret)});await call('/api/tenants');await call('/api/auth/logout','POST',undefined,204);csrf='';
 const invitedLogin=await call('/api/auth/login','POST',{username:invitePayload.username,password:'SyntheticInvited123!',tenantCode:'synthetic-smoke'});assert.equal(invitedLogin.requiresTotp,true);await call('/api/auth/login/totp','POST',{code:totp(invitedSetup.secret)});csrf=(await call('/api/auth/me')).csrfToken;
 const invitedSession=[...cookies],invitedCsrf=csrf;cookies.clear();invitationAdminSession.forEach(([k,v])=>cookies.set(k,v));csrf=invitationAdminCsrf;
 await call('/api/platform/users/'+invited.userId+'/platform-grant','PUT',{granted:true});await call('/api/platform/users/'+platformAdmin.id+'/platform-grant','PUT',{granted:false},400);
 cookies.clear();invitedSession.forEach(([k,v])=>cookies.set(k,v));csrf=invitedCsrf;assert.equal((await call('/api/platform/access')).enabled,true);
 await call('/api/platform/users/'+invited.userId+'/platform-grant','PUT',{granted:false},400);
 cookies.clear();invitationAdminSession.forEach(([k,v])=>cookies.set(k,v));csrf=invitationAdminCsrf;await call('/api/platform/users/'+invited.userId+'/platform-grant','PUT',{granted:false});
 cookies.clear();invitedSession.forEach(([k,v])=>cookies.set(k,v));csrf=invitedCsrf;assert.equal((await call('/api/platform/access')).enabled,false);await call('/api/platform/users','GET',undefined,403);
 cookies.clear();invitationAdminSession.forEach(([k,v])=>cookies.set(k,v));csrf=invitationAdminCsrf;
 // Invitations may explicitly grant the default tenant, but never other tenants implicitly.
 const defaultInvite=await call('/api/platform/invitations','POST',{username:'synthetic-default-invite',displayName:'Synthetic default invite',email:'default-invite@example.invalid',tenantId:home,roleId:homeViewer.id},201);
 const defaultToken=await syntheticSmtp.tokenFor('default-invite@example.invalid');cookies.clear();csrf='';await call('/api/auth/invitation/accept','POST',{token:defaultToken,password:'SyntheticDefault123!'});
 await call('/api/auth/login','POST',{username:'synthetic-default-invite',password:'SyntheticDefault123!'});csrf=(await call('/api/auth/me')).csrfToken;assert.equal((await call('/api/tenants')).selectedTenantId,home);await call('/api/companies','POST',{legalName:'FORBIDDEN DEFAULT INVITE WRITE'},403);
 cookies.clear();invitationAdminSession.forEach(([k,v])=>cookies.set(k,v));csrf=invitationAdminCsrf;
 const newIdentityPayload={username:'synthetic-platform-new',displayName:'Synthetic platform new',email:'new@example.invalid',password:'SyntheticOnly789!'};
 await call('/api/platform/users','POST',{...newIdentityPayload,email:''},400);
 const newIdentity=await call('/api/platform/users','POST',newIdentityPayload,201);assert.equal(newIdentity.platformAdministrator,false);assert.equal(newIdentity.active,true);assert.equal(newIdentity.totpEnabled,false);assert.ok(!('passwordHash' in newIdentity));
 await call('/api/platform/users','POST',newIdentityPayload,400);
 await call('/api/platform/users/'+platformAdmin.id+'/access','PUT',{active:false},400);
 await call('/api/platform/users/'+isolated.id+'/access','PUT',{active:false});
 const adminSession=[...cookies],adminCsrf=csrf;cookies.clear();viewerSession.forEach(([k,v])=>cookies.set(k,v));csrf=viewerCsrf;
 await call('/api/tenants','GET',undefined,401); // Revoke the already-open viewer session.
 cookies.clear();adminSession.forEach(([k,v])=>cookies.set(k,v));csrf=adminCsrf;
 await call('/api/platform/users/'+isolated.id+'/access','PUT',{active:true});
 const creationAdminSession=[...cookies],creationAdminCsrf=csrf;cookies.clear();csrf='';
 await call('/api/auth/login','POST',{username:newIdentity.username,password:newIdentityPayload.password},401);
 await call('/api/auth/login','POST',{username:newIdentity.username,password:newIdentityPayload.password,tenantCode:'synthetic-smoke'},401);
 cookies.clear();creationAdminSession.forEach(([k,v])=>cookies.set(k,v));csrf=creationAdminCsrf;
 // Business access is granted only through the existing, independently authorized membership workflow.
 await call('/api/users/memberships/shared','POST',{username:newIdentity.username,roleId:viewer.id});
 cookies.clear();csrf='';
 await call('/api/auth/login','POST',{username:newIdentity.username,password:newIdentityPayload.password});
 assert.equal((await call('/api/tenants')).selectedTenantId,created.id);
 await call('/api/auth/login','POST',{username:newIdentity.username,password:newIdentityPayload.password,tenantCode:'synthetic-smoke'});csrf=(await call('/api/auth/me')).csrfToken;
 assert.equal((await call('/api/tenants')).selectedTenantId,created.id);assert.equal((await call('/api/platform/access')).enabled,false);
 await call('/api/companies','POST',{legalName:'FORBIDDEN NEW IDENTITY COMPANY'},403);
 cookies.clear();csrf='';
 await call('/api/auth/login','POST',{username:isolated.username,password:'SyntheticOnly456!'});
 assert.equal((await call('/api/tenants')).workspaces.some(t=>t.id===home),false); // Global activation must not lift the local home suspension.
 await call('/api/auth/login','POST',{username:isolated.username,password:'SyntheticOnly456!',tenantCode:'default'},401);
 await call('/api/auth/login','POST',{username:isolated.username,password:'SyntheticOnly456!',tenantCode:'synthetic-smoke'});csrf=(await call('/api/auth/me')).csrfToken;
 assert.equal((await call('/api/tenants')).selectedTenantId,created.id);
 // Central lifecycle: no tenant-role shortcut, profile gates and live suspension.
 const suspensionViewerSession=[...cookies],suspensionViewerCsrf=csrf;
 const syntheticInvoice={type:'COMMERCIAL_INVOICE',documentNumber:'SYNTHETIC-INVOICE-001',documentDate:'2026-10-08',description:'Synthetic test goods only',quantity:'1',notes:'Disposable acceptance test'};
 await call('/api/lcs/'+localLc.id+'/generated-documents','POST',syntheticInvoice,403); // A Viewer must never gain generation rights from a profile.
 await call('/api/platform/tenants','GET',undefined,403);await call('/api/platform/tenants/'+created.id+'/inventory','GET',undefined,403);
 cookies.clear();adminSession.forEach(([k,v])=>cookies.set(k,v));csrf=adminCsrf;
 // Restore the explicitly assigned full local role for module tests: platform
 // authority alone must not bypass SETTINGS_MANAGE on business endpoints.
 await call('/api/users/memberships/shared/'+own.userId+'/role','PUT',{roleId:initialRoles.find(r=>r.baseRole==='ADMIN').id});
 await call('/api/tenants','GET',undefined,401);cookies.clear();csrf='';
 const lifecycleLogin=await call('/api/auth/login','POST',{username:'admin',password:'SyntheticOnly123!',tenantCode:'synthetic-smoke'});assert.equal(lifecycleLogin.requiresTotp,true);
 await call('/api/auth/login/totp','POST',{code:totp(setup.secret)});csrf=(await call('/api/auth/me')).csrfToken;
 await call('/api/tenants','POST',{code:'legacy-blocked',name:'Blocked',defaultLanguage:'en',bankEnabled:true,corporateEnabled:true},403);
 // Global administration is independent of the workspace retained in the session.
 await call('/api/platform/tenants/'+created.id,'PUT',{active:false,bankEnabled:true,corporateEnabled:true});
 const globalSession=await call('/api/platform/session');assert.equal(globalSession.username,'admin');assert.ok(globalSession.csrfToken);assert.ok(!('tenantId' in globalSession));
 assert.ok(Array.isArray(await call('/api/platform/tenants')));
 const platformPage=await call('/platform.html','GET',undefined,200,'application/json',true);assert.ok(platformPage.includes('GLOBAL SCOPE'));assert.ok(!platformPage.includes('id="tenantChooser"'));
 await call('/api/platform/tenants/'+created.id,'PUT',{active:true,bankEnabled:true,corporateEnabled:true});
 await call('/api/tenants/'+home+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 await call('/api/platform/tenants/'+home,'PUT',{active:false,bankEnabled:true,corporateEnabled:false},400);
 await call('/api/platform/tenants/'+created.id,'PUT',{active:true,bankEnabled:false,corporateEnabled:true});
 await call('/api/tenants/'+created.id+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 await call('/api/settings/approval-thresholds','GET',undefined,403);
 assert.ok(Array.isArray(await call('/api/document-templates')));assert.ok(Array.isArray(await call('/api/lcs')));
 const generatedPdf=await call('/api/lcs/'+localLc.id+'/generated-documents','POST',syntheticInvoice);
 assert.equal(generatedPdf.contentType,'application/pdf');assert.ok(generatedPdf.fileSize>0);
 assert.ok((await call('/api/documents/'+generatedPdf.id+'/content','GET',undefined,200,'application/json',true)).startsWith('%PDF-'));
 const generatedWord=await call('/api/lcs/'+localLc.id+'/generated-documents/docx','POST',syntheticInvoice);
 assert.equal(generatedWord.contentType,'application/vnd.openxmlformats-officedocument.wordprocessingml.document');assert.ok(generatedWord.fileSize>0);
 assert.ok((await call('/api/documents/'+generatedWord.id+'/content','GET',undefined,200,'application/json',true)).startsWith('PK'));
 await call('/api/platform/tenants/'+created.id,'PUT',{active:true,bankEnabled:true,corporateEnabled:false});
 await call('/api/document-templates','GET',undefined,403);assert.ok(Array.isArray(await call('/api/settings/approval-thresholds')));
 await call('/api/lcs/'+localLc.id+'/generated-documents','POST',syntheticInvoice,403); // Bank profile cannot bypass generation gate, even for an Admin.
 assert.ok((await call('/api/documents/'+generatedPdf.id+'/content','GET',undefined,200,'application/json',true)).startsWith('%PDF-')); // Existing documents remain readable.
 await call('/api/tenants/'+home+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 await call('/api/platform/tenants/'+created.id,'PUT',{active:false,bankEnabled:true,corporateEnabled:true});
 assert.equal((await call('/api/tenants')).workspaces.some(t=>t.id===created.id),false);await call('/api/platform/tenants/'+created.id+'/archive','PUT',{archived:true});const archivedTenant=(await call('/api/platform/tenants')).find(t=>t.id===created.id);assert.equal(archivedTenant.archived,true);assert.equal(archivedTenant.active,false);await call('/api/platform/tenants/'+created.id,'PUT',{active:true,bankEnabled:true,corporateEnabled:true},400);const suspendedInventory=await call('/api/platform/tenants/'+created.id+'/inventory');assert.equal(suspendedInventory.tenantId,created.id);assert.equal(suspendedInventory.deletionAllowed,false);assert.equal(suspendedInventory.categories.find(c=>c.key==='letter_of_credit').records,1);
 const lifecycleAdminSession=[...cookies],lifecycleAdminCsrf=csrf;cookies.clear();suspensionViewerSession.forEach(([k,v])=>cookies.set(k,v));csrf=suspensionViewerCsrf;
 await call('/api/lcs','GET',undefined,401);cookies.clear();csrf='';
 await call('/api/auth/login','POST',{username:isolated.username,password:'SyntheticOnly456!',tenantCode:'synthetic-smoke'},401);
 cookies.clear();lifecycleAdminSession.forEach(([k,v])=>cookies.set(k,v));csrf=lifecycleAdminCsrf;
 await call('/api/platform/tenants/'+created.id+'/archive','PUT',{archived:false});assert.equal((await call('/api/platform/tenants')).find(t=>t.id===created.id).active,false);await call('/api/platform/tenants/'+created.id,'PUT',{active:true,bankEnabled:true,corporateEnabled:true});
 await call('/api/tenants/'+created.id+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;assert.equal((await call('/api/lcs')).length,1);
 const retainedDocuments=(await call('/api/lcs/'+localLc.id+'/documents')).map(d=>d.id);assert.ok(retainedDocuments.includes(generatedPdf.id));assert.ok(retainedDocuments.includes(generatedWord.id));
const localInventory=await call('/api/platform/tenants/'+created.id+'/inventory');assert.equal(localInventory.inventoryOnly,true);assert.equal(localInventory.deletionAllowed,false);assert.equal(localInventory.globalAccountsExcluded,true);assert.equal(localInventory.protectedTenant,false);assert.ok(localInventory.sharedMemberships>=1);assert.ok(localInventory.blockers.includes('HOLDS_NOT_EVALUATED'));assert.equal(localInventory.categories.find(c=>c.key==='letter_of_credit').records,1);assert.equal(localInventory.categories.find(c=>c.key==='lc_document').records,retainedDocuments.length);assert.ok(localInventory.knownBinaryBytes>=generatedPdf.fileSize+generatedWord.fileSize);
 const homeInventory=await call('/api/platform/tenants/'+home+'/inventory');assert.equal(homeInventory.categories.find(c=>c.key==='lc_document').records,1);assert.equal(homeInventory.categories.find(c=>c.key==='lc_document').binaryBytes,Buffer.byteLength(homeContent));assert.ok(homeInventory.blockers.includes('PROTECTED_TENANT'));await call('/api/platform/tenants/'+crypto.randomUUID()+'/inventory','GET',undefined,404);assert.equal((await call('/api/tenants')).selectedTenantId,created.id);
 // Issue 113: automatic split after background extraction; original, page lineage and review status retained.
 const bundle=syntheticPdf(['COMMERCIAL INVOICE','COMMERCIAL INVOICE','PACKING LIST']);const form=new FormData();form.append('file',new Blob([bundle],{type:'application/pdf'}),'synthetic-mixed.pdf');const uploaded=await call('/api/inbox','POST',form,202,'multipart');const source=uploaded[0].id;
 const finished=await waitInbox(items=>items.filter(i=>i.sourceInboxId===source).length===2);const children=finished.filter(i=>i.sourceInboxId===source);assert.ok(children.every(i=>i.automaticallySplit&&i.classification.status!=='CONFIRMED'));assert.ok(!finished.some(i=>i.id===source));assert.deepEqual(children.map(i=>i.classification.suggestedType).sort(),['COMMERCIAL_INVOICE','PACKING_LIST']);assert.equal(await call('/api/inbox/'+source+'/content','GET',undefined,200,'application/json',true),bundle.toString());
 const invoice=children.find(i=>i.classification.suggestedType==='COMMERCIAL_INVOICE');assert.equal(invoice.sourceFromPage,1);assert.equal(invoice.sourceToPage,2);assert.equal((await call('/api/inbox/'+invoice.id+'/split-proposal')).pageCount,2);await call('/api/inbox/'+source+'/auto-split','POST',undefined,400);
 const unclear=syntheticPdf(['COMMERCIAL INVOICE','Unknown continuation','PACKING LIST']);const uncertainForm=new FormData();uncertainForm.append('file',new Blob([unclear],{type:'application/pdf'}),'synthetic-unclear.pdf');const uncertain=(await call('/api/inbox','POST',uncertainForm,202,'multipart'))[0];const reviewed=await waitInbox(items=>items.some(i=>i.id===uncertain.id&&i.extractionStatus==='EXTRACTED'));assert.ok(!reviewed.some(i=>i.sourceInboxId===uncertain.id));assert.deepEqual(await call('/api/inbox/'+uncertain.id+'/auto-split','POST'),[]);
 const manual=await call('/api/inbox/'+uncertain.id+'/split','POST',{parts:[{fromPage:1,toPage:2,documentType:'COMMERCIAL_INVOICE'},{fromPage:3,toPage:3,documentType:'PACKING_LIST'}]});assert.equal(manual.length,2);assert.ok(manual.every(i=>!i.automaticallySplit));
 // Confirmed split training replays only tenant-local, reviewed templates and survives file deletion.
 const trainingBundle=syntheticPdf(['Unusual document customer 123 description of shipped goods and detailed quantities for review','Continuation customer 123 description of packages and declared weights for careful review']);
 const trainingUpload=async()=>{const form=new FormData();form.append('file',new Blob([trainingBundle],{type:'application/pdf'}),'synthetic-training.pdf');const received=(await call('/api/inbox','POST',form,202,'multipart'))[0];await waitInbox(items=>items.some(i=>i.id===received.id&&i.extractionStatus==='EXTRACTED'));return received;};
 const trainingSource=await trainingUpload();const confirmedRanges=[{fromPage:1,toPage:1,documentType:'COMMERCIAL_INVOICE',copyNumber:null},{fromPage:2,toPage:2,documentType:'PACKING_LIST',copyNumber:null}];
 const trainedChildren=await call('/api/inbox/'+trainingSource.id+'/split','POST',{parts:confirmedRanges});
 await call('/api/inbox/'+trainedChildren[0].id,'DELETE',undefined,204);
 const repeatedTraining=await trainingUpload();const learnedProposal=await call('/api/inbox/'+repeatedTraining.id+'/split-proposal');assert.deepEqual(learnedProposal.parts,confirmedRanges);assert.ok(learnedProposal.pages.every(p=>p.classification.status==='REVIEW'&&p.classification.method==='CONFIRMED_SPLIT_PATTERN_V1'));assert.deepEqual(await call('/api/inbox/'+repeatedTraining.id+'/auto-split','POST'),[]);
 assert.equal((await call('/api/platform/tenants/'+created.id+'/inventory')).categories.find(c=>c.key==='document_split_training').records,1);
 const beforePretraining=(await call('/api/inbox')).map(i=>i.id).sort();const pretrainUpload=new FormData();pretrainUpload.append('file',new Blob([trainingBundle],{type:'application/pdf'}),'synthetic-pretraining.pdf');
 const trainingJob=await call('/api/training/document-types/jobs','POST',pretrainUpload,202,'multipart');assert.ok(trainingJob.id);const pretrain=await waitTraining(trainingJob.id);assert.deepEqual(pretrain.proposal.parts,confirmedRanges);assert.ok(pretrain.receipt);
 await call('/api/training/document-types/confirm','POST',{receipt:pretrain.receipt,parts:[{fromPage:1,toPage:1,documentType:'OTHER'},{fromPage:3,toPage:3,documentType:'OTHER'}]},400);
 assert.equal((await call('/api/platform/tenants/'+created.id+'/inventory')).categories.find(c=>c.key==='document_split_training').records,1);
 await call('/api/training/document-types/confirm','POST',{receipt:pretrain.receipt,parts:confirmedRanges});assert.deepEqual((await call('/api/inbox')).map(i=>i.id).sort(),beforePretraining);
 const splitQuality=await call('/api/training/document-types/quality');assert.equal(splitQuality.confirmations,2);assert.equal(splitQuality.measured,2);assert.equal(splitQuality.pages,4);assert.ok(splitQuality.correctedPages>=2);assert.equal(splitQuality.recent[0].method,'CONFIRMED_SPLIT_PATTERN_V1');assert.ok(splitQuality.recent.every(sample=>sample.pattern.length===12));
 assert.equal((await call('/api/platform/tenants/'+created.id+'/inventory')).categories.find(c=>c.key==='document_split_training').records,2);
 await call('/api/tenants/'+home+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 await call('/api/training/document-types/jobs/'+trainingJob.id,'GET',undefined,404);
 const foreignQuality=await call('/api/training/document-types/quality');assert.equal(foreignQuality.confirmations,0);assert.equal(foreignQuality.measured,0);assert.deepEqual(foreignQuality.recent,[]);
 const otherTraining=await trainingUpload();assert.ok((await call('/api/inbox/'+otherTraining.id+'/split-proposal')).pages.every(p=>p.classification.method!=='CONFIRMED_SPLIT_PATTERN_V1'));await call('/api/inbox/'+otherTraining.id,'DELETE',undefined,204);await call('/api/tenants/'+created.id+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 // Complete reviewed workflow: invalid split leaves source intact, valid parts retain designation,
 // previews work before/after attachment, and learned templates never inherit Original/Copy.
 await call('/api/inbox/'+repeatedTraining.id+'/split','POST',{parts:[{fromPage:1,toPage:1,documentType:'OTHER'},{fromPage:1,toPage:2,documentType:'OTHER'}]},400);
 assert.ok((await call('/api/inbox')).some(item=>item.id===repeatedTraining.id));
 const reviewedParts=[{...confirmedRanges[0],copyNumber:-1},{...confirmedRanges[1],copyNumber:2}];
 const workflowParts=await call('/api/inbox/'+repeatedTraining.id+'/split','POST',{parts:reviewedParts});assert.deepEqual(workflowParts.map(item=>item.copyNumber),[-1,2]);
 assert.equal(await call('/api/inbox/'+repeatedTraining.id+'/content','GET',undefined,200,'application/json',true),trainingBundle.toString());
 await assertPng('/api/inbox/'+workflowParts[0].id+'/pages/1/preview?enlarged=true');
 const attached=[];
 for(let index=0;index<workflowParts.length;index++){
  const result=await call('/api/inbox/'+workflowParts[index].id+'/attach','POST',{lcId:localLc.id,documentType:reviewedParts[index].documentType,copyNumber:reviewedParts[index].copyNumber});
  assert.equal(result.document.copyNumber,reviewedParts[index].copyNumber);assert.equal(result.document.documentType,reviewedParts[index].documentType);attached.push(result.document);
 }
 const documentList=await call('/api/lcs/'+localLc.id+'/documents');assert.ok(attached.every(document=>documentList.some(saved=>saved.id===document.id&&saved.copyNumber===document.copyNumber)));
 assert.ok(!(await call('/api/inbox')).some(item=>workflowParts.some(part=>part.id===item.id)));
 for(const document of attached){assert.ok((await call('/api/documents/'+document.id+'/content','GET',undefined,200,'application/json',true)).startsWith('%PDF-'));await assertPng('/api/documents/'+document.id+'/pages/1/preview',1);}
 await call('/api/documents/'+attached[0].id+'/pages/2/preview','GET',undefined,400);
 await call('/api/tenants/'+home+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 await call('/api/documents/'+attached[0].id+'/pages/1/preview','GET',undefined,404);
 await call('/api/tenants/'+created.id+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 const afterAttachmentTraining=await trainingUpload();assert.deepEqual((await call('/api/inbox/'+afterAttachmentTraining.id+'/split-proposal')).parts,confirmedRanges);
 await call('/api/inbox/'+afterAttachmentTraining.id,'DELETE',undefined,204);
 // Large DIGITAL bundle exercises real page ranges; deliberately not an OCR performance claim.
 const largeBundle=syntheticPdf(Array.from({length:42},(_,index)=>'Synthetic bundle page '+(index+1)+' customer descriptions package details and declared quantities for regression testing only'));
 const largeForm=new FormData();largeForm.append('file',new Blob([largeBundle],{type:'application/pdf'}),'synthetic-42-pages.pdf');
 const largeJob=await call('/api/training/document-types/jobs','POST',largeForm,202,'multipart');const largePreview=await waitTraining(largeJob.id);assert.equal(largePreview.proposal.pageCount,42);
 const largeRanges=[{fromPage:1,toPage:14,documentType:'BILL_OF_LADING',copyNumber:-1},{fromPage:15,toPage:28,documentType:'BILL_OF_LADING',copyNumber:-2},{fromPage:29,toPage:42,documentType:'BILL_OF_LADING',copyNumber:1}];
 await call('/api/training/document-types/confirm','POST',{receipt:largePreview.receipt,parts:largeRanges});
 const largeInboxForm=new FormData();largeInboxForm.append('file',new Blob([largeBundle],{type:'application/pdf'}),'synthetic-42-pages.pdf');const largeSource=(await call('/api/inbox','POST',largeInboxForm,202,'multipart'))[0];
 await waitInbox(items=>items.some(item=>item.id===largeSource.id&&item.extractionStatus==='EXTRACTED'));
 const largeLearned=await call('/api/inbox/'+largeSource.id+'/split-proposal');assert.equal(largeLearned.parts.length,3);assert.ok(largeLearned.parts.every(part=>part.copyNumber==null));
 const largeParts=await call('/api/inbox/'+largeSource.id+'/split','POST',{parts:largeRanges});assert.deepEqual(largeParts.map(part=>[part.sourceFromPage,part.sourceToPage,part.copyNumber]),[[1,14,-1],[15,28,-2],[29,42,1]]);
 for(const part of largeParts){const result=await call('/api/inbox/'+part.id+'/attach','POST',{lcId:localLc.id,documentType:'BILL_OF_LADING',copyNumber:part.copyNumber});await assertPng('/api/documents/'+result.document.id+'/pages/14/preview',14);}
 // Original/Copy is explicit per-file metadata, editable and never inferred.
 const copyForm=new FormData();for(const designation of [-1,-2,-3,0,2]){copyForm.append('file',new Blob([trainingBundle],{type:'application/pdf'}),'synthetic-copy.pdf');copyForm.append('types','BILL_OF_LADING');copyForm.append('copies',String(designation));}
 const copies=await call('/api/lcs/'+localLc.id+'/documents/batch','POST',copyForm,200,'multipart');assert.deepEqual(copies.map(d=>d.copyNumber),[-1,-2,-3,0,2]);
 const changedCopy=await call('/api/lcs/'+localLc.id+'/documents/'+copies[0].id,'PUT',{type:'OTHER',copyNumber:3});assert.equal(changedCopy.copyNumber,3);
 await call('/api/lcs/'+localLc.id+'/documents/'+copies[0].id,'PUT',{type:'OTHER',copyNumber:4},400);
 for(const document of copies)await call('/api/lcs/'+localLc.id+'/documents/'+document.id,'DELETE',undefined,204);
 // Full test-data purge preserves shared global identities and the other workspace.
 const purgeBody={tenantCode:'synthetic-smoke',deleteAllBusinessData:true,acknowledgeAuditAndBackupsRetained:true};
 await call('/api/platform/tenants/'+created.id,'DELETE',purgeBody,400);
 await call('/api/tenants/'+home+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 await call('/api/platform/tenants/'+home,'DELETE',{...purgeBody,tenantCode:'default'},400);
 await call('/api/platform/tenants/'+created.id,'PUT',{active:false,bankEnabled:true,corporateEnabled:true});
 await call('/api/platform/tenants/'+created.id+'/archive','PUT',{archived:true});
 await call('/api/platform/tenants/'+created.id,'DELETE',{...purgeBody,tenantCode:'wrong'},400);
 await call('/api/platform/tenants/'+created.id,'DELETE',purgeBody,204);
 assert.equal((await call('/api/platform/tenants')).some(t=>t.id===created.id),false);
 assert.ok((await call('/api/platform/users')).some(u=>u.id===isolated.id));
 assert.ok((await call('/api/lcs')).some(l=>l.id===homeLc.id));
 const purged=await call('/api/platform/tenants/'+created.id+'/inventory');
 assert.ok(purged.categories.filter(c=>c.key!=='audit_event').every(c=>c.records===0));
 assert.ok(purged.categories.find(c=>c.key==='audit_event').records>0);
 await call('/api/platform/tenants/'+created.id+'/archive','PUT',{archived:false},400);
 // Hash chain: written by the database for every audit row of the whole run, including the purged tenant's retained audit.
 const chains=await call('/api/platform/audit/chain');
 assert.ok(chains.length>=2);assert.ok(chains.every(c=>c.ok===true&&c.checked>0&&/^[0-9a-f]{64}$/.test(c.headHash)),JSON.stringify(chains));
 const tenantChain=await call('/api/audit/chain');assert.equal(tenantChain.ok,true);assert.ok(tenantChain.checked>5);
 console.log('PASS: tenant lifecycle, isolation, async pretraining, reviewed split/attachment, raster previews, Original/Copy and full disposable business-data purge.');
})().catch(error=>{console.error(error.message);process.exitCode=1;}).finally(async()=>{if(syntheticSmtp)await syntheticSmtp.close();});
