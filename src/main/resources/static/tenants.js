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
 const submit=node('button',text('tenant.create','Create tenant'));submit.type='submit';form.append(submit);
 const notice=node('p',text('tenant.identityNotice','Create global user accounts in the default tenant, then assign them to another tenant under Users. New tenants start with no business data.'));
 section.append(heading,notice,message,list,form);document.querySelector('main').append(section);
 nav.onclick=()=>{appNavigate('tenants',nav);refresh();};
 const header=document.createElement('label');header.className='tenant-workspace-selector';header.append(node('span',text('tenant.workspace','Workspace')));
 const chooser=document.createElement('select');chooser.setAttribute('aria-label',text('tenant.workspace','Workspace'));header.append(chooser);document.querySelector('.header-actions').prepend(header);
 let overview,busy=false;
 async function switchTo(id){
  if(busy||id===overview.selectedTenantId)return;busy=true;chooser.disabled=true;
  try{await json('/api/tenants/'+encodeURIComponent(id)+'/select',{method:'POST'});location.reload();}
  catch(error){message.textContent=error.message;chooser.value=overview.selectedTenantId;appNavigate('tenants',nav);}
  finally{busy=false;chooser.disabled=false;}
 }
 chooser.onchange=()=>switchTo(chooser.value);
 async function refresh(){
  try{overview=await json('/api/tenants');chooser.replaceChildren();list.replaceChildren();form.hidden=!overview.creationEnabled;
   overview.workspaces.forEach(workspace=>{const option=node('option',workspace.name);option.value=workspace.id;chooser.append(option);
    const row=document.createElement('article');row.className='membership-row';row.append(node('b',workspace.name),node('small',workspace.code));
    const button=node('button',workspace.id===overview.selectedTenantId?text('tenant.selected','Selected'):text('tenant.open','Open workspace'));button.type='button';button.className='secondary';button.disabled=workspace.id===overview.selectedTenantId;button.onclick=()=>switchTo(workspace.id);row.append(button);list.append(row);
   });chooser.value=overview.selectedTenantId;
  }catch(error){message.textContent=error.message;form.hidden=true;chooser.disabled=true;}
 }
 form.onsubmit=async event=>{event.preventDefault();if(busy||!overview?.creationEnabled)return;busy=true;submit.disabled=true;message.textContent='';
  try{const values=Object.fromEntries(new FormData(form));values.bankEnabled=true;values.corporateEnabled=false;const created=await json('/api/tenants',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(values)});form.reset();await refresh();message.textContent=text('tenant.created','Tenant created: ')+created.name;}
  catch(error){message.textContent=error.message;}finally{busy=false;submit.disabled=false;}
 };
 await refresh();
}
