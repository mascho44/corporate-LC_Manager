/* Workspace administration: a page first, never an automatic modal. */
async function setupTenants(){
 const text=(key,fallback)=>{const value=globalThis.LcI18n?.t(key);return value&&value!==key?value:fallback;};
 const node=(tag,value)=>{const n=document.createElement(tag);n.textContent=value;return n;};
 const nav=node('button',text('tenant.title','Tenants'));nav.type='button';nav.id='appNavTenants';
 document.querySelector('#appNav nav').append(nav);
 const section=document.createElement('section');section.id='tenantSection';section.className='panel hidden';
 const heading=node('h2',text('tenant.title','Tenants'));const message=node('p','');message.setAttribute('role','status');const list=document.createElement('div');
 const form=document.createElement('form');form.className='form-grid';
 const fields=[['code','tenant.code','Tenant code'],['name','tenant.name','Tenant name']];
 fields.forEach(([name,key,label])=>{const wrapper=node('label',text(key,label)),input=document.createElement('input');input.name=name;input.required=true;input.maxLength=name==='code'?50:255;if(name==='code')input.pattern='[a-z][a-z0-9-]{1,49}';wrapper.append(input);form.append(wrapper);});
 const language=node('label',text('tenant.language','Default language')),select=document.createElement('select');select.name='defaultLanguage';[['en','English'],['de','Deutsch']].forEach(([value,label])=>{const option=node('option',label);option.value=value;select.append(option);});language.append(select);form.append(language);
 const profileLabel=node('label',text('tenant.profile','Tenant profile')),profile=document.createElement('select');profile.name='profile';profile.id='tenantCreationProfile';profile.value='bank';
 [['bank','tenant.profileBank','Bank'],['corporate','tenant.profileCorporate','Corporate'],['combined','tenant.profileCombined','Bank & Corporate']].forEach(([value,key,label])=>{const option=node('option',text(key,label));option.value=value;profile.append(option);});profile.value='bank';profileLabel.append(profile);form.append(profileLabel);
 const profileNotice=node('p',text('tenant.profileNotice','The profile is informational. It does not enable modules or grant permissions. It cannot currently be changed after creation.'));profileNotice.id='tenantProfileNotice';profile.setAttribute('aria-describedby',profileNotice.id);form.append(profileNotice);
 const submit=node('button',text('tenant.create','Create tenant'));submit.type='submit';form.append(submit);
 const notice=node('p',text('tenant.identityNotice','Global accounts and invitations are managed in Platform administration. Local roles and memberships are managed under Users. New tenants start without business data.'));
 section.append(heading,notice,message,list,form);document.querySelector('main').append(section);
 const retry=node('button',text('tenant.retry','Reload tenant information'));retry.id='tenantRetry';retry.type='button';retry.className='secondary';retry.hidden=true;retry.onclick=()=>refresh();section.append(retry);
 const createdInfo=document.createElement('section');createdInfo.id='tenantCreatedInfo';createdInfo.className='tenant-readiness';createdInfo.hidden=true;section.append(createdInfo);
 const loginInfo=document.createElement('section');loginInfo.id='tenantLoginInfo';loginInfo.className='tenant-readiness';loginInfo.hidden=true;section.append(loginInfo);
 const settingsForm=document.createElement('form');settingsForm.id='tenantSettingsForm';settingsForm.className='form-grid';settingsForm.hidden=true;
 const settingsTitle=node('h3',text('tenant.settings','Current tenant settings'));
 const nameLabel=node('label',text('tenant.name','Tenant name')),settingsName=document.createElement('input');settingsName.name='name';settingsName.required=true;settingsName.maxLength=255;nameLabel.append(settingsName);
 const languageLabel=node('label',text('tenant.language','Default language')),settingsLanguage=document.createElement('select');settingsLanguage.name='defaultLanguage';[['en','English'],['de','Deutsch']].forEach(([value,label])=>{const option=node('option',label);option.value=value;settingsLanguage.append(option);});languageLabel.append(settingsLanguage);
 const saveSettings=node('button',text('tenant.saveSettings','Save settings'));saveSettings.type='submit';settingsForm.append(settingsTitle,nameLabel,languageLabel,saveSettings);section.append(settingsForm);
 const readiness=document.createElement('section');readiness.id='tenantReadiness';readiness.className='tenant-readiness';readiness.hidden=true;section.append(readiness);
 async function refreshReadiness(generation){
  if(generation!==refreshGeneration)return;
  readiness.replaceChildren();readiness.hidden=!(settings?.editingEnabled&&typeof can==='function'&&can('USER_MANAGE')&&can('SETTINGS_MANAGE'));if(readiness.hidden)return;
  readiness.append(node('h3',text('tenant.readiness','Tenant setup')),node('p',text('tenant.readinessNote','Presence checks only. These do not confirm the completeness or validity of company data and templates.')));
  try{const status=await json('/api/tenants/current/readiness');if(generation!==refreshGeneration)return;
   [['companies','tenant.companies','Company records','#appNavCompany'],['templates','tenant.templates','Document templates','#appNavTemplates'],['activeMembers','tenant.members','Active members','#appNavUsers']].forEach(([key,label,fallback,target])=>{
    const count=Number(status[key])||0,row=document.createElement('article');row.className='membership-row';row.append(node('b',text(label,fallback)),node('span',(count>0?text('tenant.present','Present'):text('tenant.missing','Not yet configured'))+' · '+count));
    const open=node('button',text('tenant.configure','Open administration'));open.type='button';open.className='secondary';open.disabled=key==='templates'&&settings?.corporateEnabled!==true;open.onclick=()=>{if(!open.disabled)document.querySelector(target)?.click?.();};row.append(open);readiness.append(row);
   });
  }catch(error){if(generation!==refreshGeneration)return;const notice=node('p',error.message);notice.setAttribute('role','alert');readiness.append(notice);}
 }
 nav.onclick=()=>{appNavigate('tenants',nav);return refresh();};
 const header=document.createElement('label');header.className='tenant-workspace-selector';header.append(node('span',text('tenant.workspace','Workspace')));
 const chooser=document.createElement('select');chooser.setAttribute('aria-label',text('tenant.workspace','Workspace'));header.append(chooser);document.querySelector('.header-actions').prepend(header);
 let overview,settings,busy=false,refreshGeneration=0;
 async function switchTo(id){
  if(busy||!overview||id===overview.selectedTenantId)return;
  const target=overview.workspaces.find(workspace=>workspace.id===id);if(!target){chooser.value=overview.selectedTenantId;return;}
  busy=true;chooser.disabled=true;
  try{
   const accepted=await confirmAction(text('tenant.switchWarning','Switch workspace? The page will reload. Unsaved changes may be lost.')+'\n\n'+target.name+' ('+target.code+')',{title:text('tenant.switchTitle','Confirm workspace switch'),cancel:text('tenant.switchCancel','Stay here'),confirm:text('tenant.switchConfirm','Switch workspace')});
   if(!accepted){chooser.value=overview.selectedTenantId;return;}
   await json('/api/tenants/'+encodeURIComponent(id)+'/select',{method:'POST'});location.reload();}
  catch(error){message.textContent=error.message;chooser.value=overview.selectedTenantId;appNavigate('tenants',nav);}
  finally{busy=false;chooser.disabled=false;}
 }
 chooser.onchange=()=>switchTo(chooser.value);
 async function refresh(){
  const generation=++refreshGeneration;settings=undefined;settingsForm.hidden=true;readiness.hidden=true;readiness.replaceChildren();
  section.setAttribute('aria-busy','true');retry.hidden=true;form.hidden=true;message.textContent=text('tenant.loading','Loading tenant information…');
  loginInfo.hidden=true;loginInfo.replaceChildren();
  try{const nextOverview=await json('/api/tenants');if(generation!==refreshGeneration)return;overview=nextOverview;chooser.disabled=busy;chooser.replaceChildren();list.replaceChildren();form.hidden=!overview.creationEnabled;
   overview.workspaces.forEach(workspace=>{const option=node('option',workspace.name);option.value=workspace.id;chooser.append(option);
    const row=document.createElement('article');row.className='membership-row';row.append(node('b',workspace.name),node('small',workspace.code));
    const button=node('button',workspace.id===overview.selectedTenantId?text('tenant.selected','Selected'):text('tenant.open','Open workspace'));button.type='button';button.className='secondary';button.disabled=workspace.id===overview.selectedTenantId;button.onclick=()=>switchTo(workspace.id);row.append(button);list.append(row);
   });chooser.value=overview.selectedTenantId;
   const nextSettings=await json('/api/tenants/current/settings');if(generation!==refreshGeneration)return;settings=nextSettings;settingsForm.hidden=!settings.editingEnabled;settingsName.value=settings.name;settingsLanguage.value=settings.defaultLanguage;
   if(typeof applyWorkspaceProfile==='function')applyWorkspaceProfile(settings);
   const current=overview.workspaces.find(workspace=>workspace.id===overview.selectedTenantId);
   if(current){loginInfo.hidden=false;loginInfo.append(node('h3',text('tenant.loginInfo','Access to this tenant')),node('p',current.name),node('p',text('tenant.loginCode','Tenant code for login: ')+current.code),node('p',text('tenant.loginHelp','On the login page, enter this code in the optional tenant field. Use your existing username, password and two-factor authentication when required. Leaving the tenant field blank opens the default tenant, not necessarily this workspace.')),node('p',text('tenant.accessHelp','A tenant administrator must first assign your existing account to a role under Users. Creating a tenant or knowing its code does not grant access.')));}
   await refreshReadiness(generation);if(generation===refreshGeneration)message.textContent='';
  }catch(error){if(generation!==refreshGeneration)return;message.textContent=error.message;form.hidden=true;chooser.disabled=true;retry.hidden=false;}
  finally{if(generation===refreshGeneration)section.setAttribute('aria-busy','false');}
 }
 form.onsubmit=async event=>{event.preventDefault();if(busy||!overview?.creationEnabled)return;busy=true;submit.disabled=true;message.textContent='';createdInfo.hidden=true;createdInfo.replaceChildren();
  try{const values=Object.fromEntries(new FormData(form));if(!['bank','corporate','combined'].includes(profile.value))throw Error(text('tenant.profileInvalid','Select a supported tenant profile.'));delete values.profile;values.bankEnabled=profile.value!=='corporate';values.corporateEnabled=profile.value!=='bank';const created=await json('/api/tenants',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(values)});form.reset();await refresh();message.textContent=text('tenant.created','Tenant created: ')+created.name;
   if(overview.workspaces.some(workspace=>workspace.id===created.id)){createdInfo.hidden=false;createdInfo.append(node('h3',created.name),node('p',text('tenant.createdNext','Open the new workspace to configure company records, document templates and user roles. Existing business data is not copied.')));const open=node('button',text('tenant.open','Open workspace'));open.id='tenantCreatedOpen';open.type='button';open.onclick=()=>switchTo(created.id);createdInfo.append(open);}}
  catch(error){message.textContent=error.message;}finally{busy=false;submit.disabled=false;chooser.disabled=false;}
 };
 settingsForm.onsubmit=async event=>{event.preventDefault();if(busy||!settings?.editingEnabled)return;busy=true;saveSettings.disabled=true;message.textContent='';
  try{await json('/api/tenants/current/settings',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:settingsName.value,defaultLanguage:settingsLanguage.value})});await refresh();message.textContent=text('tenant.settingsSaved','Settings saved. The default language applies to users without a personal language preference.');}
  catch(error){message.textContent=error.message;}finally{busy=false;saveSettings.disabled=false;chooser.disabled=false;}
 };
 await refresh();
}
