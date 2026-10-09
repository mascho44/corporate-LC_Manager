/* Global account access is separate from local membership/role administration. */
async function setupPlatformAdministration(standalone=false){
 const text=(key,fallback)=>{const value=globalThis.LcI18n?.t(key);return value&&value!==key?value:fallback;};
 const node=(tag,key,fallback)=>{const n=document.createElement(tag);n.textContent=text(key,fallback);if(key)n.dataset.i18n=key;return n;};
 let access;try{access=await json('/api/platform/access');}catch{return;}if(access.enabled!==true)return;
 const nav=node('button','platform.title','Platform administration');nav.id='appNavPlatform';nav.type='button';document.querySelector('#appNav nav').append(nav);
 if(!standalone){nav.onclick=()=>location.assign('/platform.html');return;}
 const section=document.createElement('section');section.id='platformSection';section.className='panel hidden';
 const status=node('p','','');status.setAttribute('role','status');const list=document.createElement('div');let generation=0;
 const reload=node('button','platform.reload','Reload accounts');reload.type='button';reload.className='secondary';
 section.append(node('h2','platform.title','Platform administration'),node('p','platform.notice','Global account suspension blocks access in every tenant. Activation does not restore suspended local memberships.'),reload,status,list);document.querySelector('main').append(section);
 const form=document.createElement('form');form.className='platform-create-form';
 form.append(node('h3','platform.inviteTitle','Invite user'),node('p','platform.inviteNotice','The recipient chooses their own password. The selected tenant and role are activated only after acceptance. No platform rights are granted.'));
 const inputs={};
 for(const [key,type,max] of [['username','text',100],['displayName','text',255],['email','email',255]]){
  const label=node('label','platform.field.'+key,key);const input=document.createElement('input');input.id='platformCreate'+key;input.type=type;input.required=true;input.maxLength=max;input.autocomplete=key==='password'?'new-password':'off';if(key==='password')input.minLength=10;label.append(input);form.append(label);inputs[key]=input;
 }
 for(const key of ['tenantId','roleId']){const label=node('label','platform.field.'+key,key==='tenantId'?'Tenant':'Role');const select=document.createElement('select');select.id='platformCreate'+key;select.required=true;select.disabled=true;label.append(select);form.append(label);inputs[key]=select;}
 const preview=node('small','','');form.append(preview);let choices=[];
 const option=(value,label)=>{const o=document.createElement('option');o.value=value;o.textContent=label;return o;};
 const fillRoles=()=>{const tenant=choices.find(c=>c.id===inputs.tenantId.value);inputs.roleId.replaceChildren(option('',text('platform.chooseRole','Choose a role')),...(tenant?.roles||[]).map(r=>option(r.id,r.name)));inputs.roleId.value='';inputs.roleId.disabled=!tenant;preview.textContent='';};
 inputs.tenantId.onchange=fillRoles;inputs.roleId.onchange=()=>{const role=choices.find(c=>c.id===inputs.tenantId.value)?.roles.find(r=>r.id===inputs.roleId.value);if(!role){preview.textContent='';return;}preview.textContent=text('platform.permissions','Permissions: ')+(Array.isArray(role.permissions)?role.permissions.map(p=>globalThis.LcI18n?.t('permission.'+p)||p).join(' · ')||text('platform.noWritePermissions','No write permissions'):text('platform.permissionsUnavailable','Permissions unavailable'));};
 const create=node('button','platform.invite','Send invitation');create.type='submit';create.disabled=true;const createStatus=node('p','','');createStatus.setAttribute('role','status');form.append(create,createStatus);section.append(form);
 const invitationList=document.createElement('div');section.append(node('h3','platform.invitations','Pending invitations'),invitationList);
 let creating=false;
 form.onsubmit=async event=>{event.preventDefault();if(creating||!form.reportValidity())return;creating=true;create.disabled=true;createStatus.textContent=text('platform.creating','Creating account…');
  const payload=Object.fromEntries(Object.entries(inputs).map(([key,input])=>[key,input.value]));
  try{await json('/api/platform/invitations',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});form.reset();inputs.tenantId.value='';fillRoles();createStatus.textContent=text('platform.invited','Invitation created. Check its delivery status below.');await refresh();}
  catch(failure){createStatus.textContent=failure.message;}finally{creating=false;create.disabled=choices.length===0;}
 };
 async function refresh(){
  const request=++generation;list.replaceChildren();invitationList.replaceChildren();status.textContent=text('platform.loading','Loading global accounts…');section.setAttribute('aria-busy','true');
  try{const [accounts,pending,available]=await Promise.all([json('/api/platform/users'),json('/api/platform/invitations'),json('/api/platform/invitations/choices')]);if(request!==generation)return;
   const selectedTenant=inputs.tenantId.value,selectedRole=inputs.roleId.value;choices=available;inputs.tenantId.replaceChildren(option('',text('platform.chooseTenant','Choose a tenant')),...choices.map(c=>option(c.id,c.name+' · '+c.code)));inputs.tenantId.value=choices.some(c=>c.id===selectedTenant)?selectedTenant:'';inputs.tenantId.disabled=false;fillRoles();if(choices.find(c=>c.id===inputs.tenantId.value)?.roles.some(r=>r.id===selectedRole))inputs.roleId.value=selectedRole;inputs.roleId.onchange();create.disabled=creating||choices.length===0;
   accounts.forEach(account=>{
    const row=document.createElement('article');row.className='membership-row';const identity=document.createElement('div');
    const name=node('b','','');name.textContent=account.displayName;const username=node('small','','');username.textContent=account.username;const email=node('small','','');email.textContent=account.email||'';identity.append(name,username,email);
    if(account.platformAdministrator)identity.append(node('small','platform.administrator','Platform administrator'));
    const badge=node('span',account.active?'platform.active':'platform.inactive',account.active?'Globally active':'Globally suspended');badge.className='import-status '+(account.active?'success':'rejected');
    if(account.invitationPending)identity.append(node('small','platform.invitationPending','Invitation not yet accepted'));
    const toggle=node('button',account.active?'platform.suspend':'platform.activate',account.active?'Suspend global account':'Activate global account');toggle.type='button';toggle.className=account.active?'danger':'secondary';toggle.disabled=account.username===currentUser.username||account.invitationPending===true;
    const error=node('small','','');error.setAttribute('role','alert');error.className='error';let busy=false;
    toggle.onclick=async()=>{if(busy||toggle.disabled)return;const version=generation;
     const question=text(account.active?'platform.confirmSuspend':'platform.confirmActivate',account.active?'Suspend this global account in ALL tenants?':'Activate this global account? Local membership suspensions remain unchanged.')+'\n\n'+account.username;
     busy=true;toggle.disabled=true;
     try{if(!await confirmAction(question))return;await json('/api/platform/users/'+encodeURIComponent(account.id)+'/access',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({active:!account.active})});if(version===generation)await refresh();}
     catch(failure){if(version===generation)error.textContent=failure.message;}finally{busy=false;toggle.disabled=account.username===currentUser.username||account.invitationPending===true;}
    };
    const grant=node('button',account.platformAdministrator?'platform.revokeGrant':'platform.grant',account.platformAdministrator?'Revoke platform rights':'Grant platform rights');grant.type='button';grant.className='secondary';const eligible=account.platformAdministrator?account.username!==currentUser.username:account.active&&account.totpEnabled&&!account.invitationPending;grant.disabled=!eligible;grant.title=text('platform.grantRequirement','Requires an active account with two-factor authentication. You cannot revoke your own platform access.');
    grant.onclick=async()=>{if(busy||grant.disabled)return;const version=generation;busy=true;grant.disabled=true;try{if(!await confirmAction(text(account.platformAdministrator?'platform.confirmRevoke':'platform.confirmGrant',account.platformAdministrator?'Revoke global platform administration rights?':'Grant global platform administration rights for ALL tenants?')+'\n\n'+account.username))return;await json('/api/platform/users/'+encodeURIComponent(account.id)+'/platform-grant',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({granted:!account.platformAdministrator})});if(version===generation)await refresh();}catch(failure){if(version===generation)error.textContent=failure.message;}finally{busy=false;grant.disabled=!eligible;}};
    row.append(identity,badge,toggle,grant,error);list.append(row);
   });status.textContent=text('platform.loaded','Global accounts: ')+accounts.length;
   pending.forEach(invitation=>{const row=document.createElement('article');row.className='membership-row';const info=node('div','','');info.textContent=invitation.username+' · '+invitation.tenantName+' · '+invitation.roleName+' · '+text('platform.delivery.'+invitation.status,invitation.status)+' · '+new Date(invitation.expiresAt).toLocaleString();info.textContent+=' · '+text('platform.deliveryAttempts','Attempts')+': '+(invitation.attempts||0);if(invitation.nextAttemptAt)info.textContent+=' · '+text('platform.nextAttempt','Next attempt')+': '+new Date(invitation.nextAttemptAt).toLocaleString();if(invitation.errorCode)info.textContent+=' · '+text('platform.deliveryError.'+invitation.errorCode,text('platform.delivery.MAIL_FAILED','Email delivery failed'));const error=node('small','','');error.setAttribute('role','alert');let busy=false;
    const actions=[];for(const action of ['resend','revokeInvitation']){const button=node('button','platform.'+action,action==='resend'?'Resend invitation':'Revoke invitation');button.type='button';button.className=action==='resend'?'secondary':'danger';actions.push(button);button.onclick=async()=>{if(busy)return;busy=true;actions.forEach(b=>b.disabled=true);const version=generation;try{if(!await confirmAction(text(action==='resend'?'platform.confirmResend':'platform.confirmRevokeInvitation',action==='resend'?'Send a new invitation? The previous link will stop working.':'Revoke this invitation? The account remains blocked.')+'\n\n'+invitation.username))return;await json('/api/platform/invitations/'+encodeURIComponent(invitation.userId)+(action==='resend'?'/resend':''),{method:action==='resend'?'POST':'DELETE'});if(version===generation)await refresh();}catch(failure){if(version===generation)error.textContent=failure.message;}finally{busy=false;actions.forEach(b=>b.disabled=false);}};}row.append(info,...actions,error);invitationList.append(row);
   });if(pending.length===0)invitationList.append(node('p','platform.noInvitations','No pending invitations.'));
  }catch(failure){if(request===generation)status.textContent=failure.message||text('platform.error','Global accounts could not be loaded.');}
  finally{if(request===generation)section.setAttribute('aria-busy','false');}
 }
 const refreshTenants=await setupPlatformTenants(section);
 reload.onclick=()=>Promise.all([refresh(),refreshTenants()]);nav.onclick=()=>{section.classList.remove('hidden');return Promise.all([refresh(),refreshTenants()]);};
}
