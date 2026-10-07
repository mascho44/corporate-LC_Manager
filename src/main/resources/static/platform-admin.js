/* Global account access is separate from local membership/role administration. */
async function setupPlatformAdministration(){
 const text=(key,fallback)=>{const value=globalThis.LcI18n?.t(key);return value&&value!==key?value:fallback;};
 const node=(tag,key,fallback)=>{const n=document.createElement(tag);n.textContent=text(key,fallback);if(key)n.dataset.i18n=key;return n;};
 let access;try{access=await json('/api/platform/access');}catch{return;}if(access.enabled!==true)return;
 const nav=node('button','platform.title','Platform administration');nav.id='appNavPlatform';nav.type='button';document.querySelector('#appNav nav').append(nav);
 const section=document.createElement('section');section.id='platformSection';section.className='panel hidden';
 const status=node('p','','');status.setAttribute('role','status');const list=document.createElement('div');let generation=0;
 const reload=node('button','platform.reload','Reload accounts');reload.type='button';reload.className='secondary';
 section.append(node('h2','platform.title','Platform administration'),node('p','platform.notice','Global account suspension blocks access in every tenant. Activation does not restore suspended local memberships.'),reload,status,list);document.querySelector('main').append(section);
 const form=document.createElement('form');form.className='platform-create-form';
 form.append(node('h3','platform.createTitle','Create global account'),node('p','platform.createNotice','No tenant access is granted. Assign the identity under Users in the intended tenant. Invitation links will follow separately.'));
 const inputs={};
 for(const [key,type,max] of [['username','text',100],['displayName','text',255],['email','email',255],['password','password',200]]){
  const label=node('label','platform.field.'+key,key);const input=document.createElement('input');input.id='platformCreate'+key;input.type=type;input.required=true;input.maxLength=max;input.autocomplete=key==='password'?'new-password':'off';if(key==='password')input.minLength=10;label.append(input);form.append(label);inputs[key]=input;
 }
 const create=node('button','platform.create','Create account');create.type='submit';const createStatus=node('p','','');createStatus.setAttribute('role','status');form.append(create,createStatus);section.append(form);
 let creating=false;
 form.onsubmit=async event=>{event.preventDefault();if(creating||!form.reportValidity())return;creating=true;create.disabled=true;createStatus.textContent=text('platform.creating','Creating account…');
  const payload=Object.fromEntries(Object.entries(inputs).map(([key,input])=>[key,input.value]));
  try{await json('/api/platform/users',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});form.reset();createStatus.textContent=text('platform.created','Account created without tenant access. Assign it in the intended tenant.');await refresh();}
  catch(failure){createStatus.textContent=failure.message;}finally{inputs.password.value='';creating=false;create.disabled=false;}
 };
 async function refresh(){
  const request=++generation;list.replaceChildren();status.textContent=text('platform.loading','Loading global accounts…');section.setAttribute('aria-busy','true');
  try{const accounts=await json('/api/platform/users');if(request!==generation)return;
   accounts.forEach(account=>{
    const row=document.createElement('article');row.className='membership-row';const identity=document.createElement('div');
    const name=node('b','','');name.textContent=account.displayName;const username=node('small','','');username.textContent=account.username;const email=node('small','','');email.textContent=account.email||'';identity.append(name,username,email);
    if(account.platformAdministrator)identity.append(node('small','platform.administrator','Platform administrator'));
    const badge=node('span',account.active?'platform.active':'platform.inactive',account.active?'Globally active':'Globally suspended');badge.className='import-status '+(account.active?'success':'rejected');
    const toggle=node('button',account.active?'platform.suspend':'platform.activate',account.active?'Suspend global account':'Activate global account');toggle.type='button';toggle.className=account.active?'danger':'secondary';toggle.disabled=account.username===currentUser.username;
    const error=node('small','','');error.setAttribute('role','alert');error.className='error';let busy=false;
    toggle.onclick=async()=>{if(busy||toggle.disabled)return;const version=generation;
     const question=text(account.active?'platform.confirmSuspend':'platform.confirmActivate',account.active?'Suspend this global account in ALL tenants?':'Activate this global account? Local membership suspensions remain unchanged.')+'\n\n'+account.username;
     busy=true;toggle.disabled=true;
     try{if(!await confirmAction(question))return;await json('/api/platform/users/'+encodeURIComponent(account.id)+'/access',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({active:!account.active})});if(version===generation)await refresh();}
     catch(failure){if(version===generation)error.textContent=failure.message;}finally{busy=false;toggle.disabled=account.username===currentUser.username;}
    };
    row.append(identity,badge,toggle,error);list.append(row);
   });status.textContent=text('platform.loaded','Global accounts: ')+accounts.length;
  }catch(failure){if(request===generation)status.textContent=failure.message||text('platform.error','Global accounts could not be loaded.');}
  finally{if(request===generation)section.setAttribute('aria-busy','false');}
 }
 reload.onclick=refresh;nav.onclick=()=>{appNavigate('platform',nav);return refresh();};
}
