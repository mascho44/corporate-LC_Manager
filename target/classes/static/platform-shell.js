'use strict';
let currentUser=null,csrfToken='';
async function json(url,options={}){
 if(!url.startsWith('/api/platform/'))throw new Error('Only global platform APIs are available in this area.');
 options.headers={...(options.headers||{})};if(options.method&&options.method!=='GET'&&csrfToken)options.headers['X-CSRF-TOKEN']=csrfToken;
 const response=await fetch(url,options);
 if(response.status===401){location.replace('/platform-login.html');throw new Error('Please sign in again.');}
 if(!response.ok){const body=await response.json().catch(()=>({}));throw new Error(body.error||'HTTP '+response.status);}
 return response.status===204?null:response.json();
}
document.addEventListener('DOMContentLoaded',async()=>{
 const error=document.getElementById('platformError');
 try{
  currentUser=await json('/api/platform/session');csrfToken=currentUser.csrfToken;
  // A platform administrator without any tenant has no workspace to open: the link would only end the session.
  try{const access=await json('/api/platform/access');if(access.tenantless)document.querySelector('.header-actions a.button-link')?.remove();}catch{}
  document.getElementById('platformIdentity').textContent=currentUser.username;
  await globalThis.LcI18n?.setLanguage('en');
  await setupPlatformAdministration(true);
  const nav=document.getElementById('appNavPlatform');if(!nav)throw new Error('Platform access is unavailable.');await nav.onclick();await globalThis.LcPlatformUsers?.setup?.(document.getElementById('platformSection'));buildSubnav();
  buildPasswordSection();
  buildDenialsSection();
  document.getElementById('platformLogout').onclick=async event=>{const button=event.currentTarget;button.disabled=true;try{await json('/api/platform/logout',{method:'POST'});location.replace('/platform-login.html');}catch(failure){error.textContent=failure.message;button.disabled=false;}};
 }catch(failure){error.textContent=failure.message;}
});

/* Quick links to the sections of the console; built from the rendered headings. */
function buildSubnav(){
 const section=document.getElementById('platformSection');if(!section)return;
 const nav=document.createElement('nav');nav.className='platform-subnav';nav.setAttribute('aria-label','Bereiche');
 section.querySelectorAll('h3').forEach((heading,index)=>{
  heading.id=heading.id||'platform-part-'+index;
  const link=document.createElement('a');link.href='#'+heading.id;link.textContent=heading.textContent;nav.append(link);
 });
 if(nav.children.length)section.before(nav);
}

/* Own password: platform accounts belong to no tenant, so they change it here. The change ends the session. */
function buildPasswordSection(){
 const host=document.getElementById('platformSection');if(!host||document.getElementById('platformPasswordForm'))return;
 const node=(tag,text)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;return n;};
 const form=document.createElement('form');form.id='platformPasswordForm';form.className='form-grid';
 const inputs={};
 form.append(node('h3','Eigenes Passwort ändern'));
 for(const [key,label] of [['current','Aktuelles Passwort'],['next','Neues Passwort (mind. 10 Zeichen, Groß-/Kleinbuchstaben, Ziffer)'],['again','Neues Passwort wiederholen']]){
  const wrapper=node('label',label),input=document.createElement('input');input.type='password';input.required=true;input.maxLength=200;input.autocomplete=key==='current'?'current-password':'new-password';wrapper.append(input);form.append(wrapper);inputs[key]=input;
 }
 const button=node('button','Passwort ändern');button.type='submit';const status=node('p','');status.setAttribute('role','status');form.append(button,status);
 form.onsubmit=async event=>{
  event.preventDefault();if(button.disabled)return;
  if(inputs.next.value!==inputs.again.value){status.textContent='Die neuen Passwörter stimmen nicht überein.';return;}
  button.disabled=true;status.textContent='';
  try{
   await json('/api/platform/password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({currentPassword:inputs.current.value,newPassword:inputs.next.value})});
   status.textContent='Passwort geändert. Bitte neu anmelden.';setTimeout(()=>location.replace('/platform-login.html'),1500);
  }catch(failure){status.textContent=failure.message;button.disabled=false;}
 };
 host.append(form);
}

/* Latest refused requests (401/403) with the likely reason: find the cause of a "forbidden" without server access. */
function buildDenialsSection(){
 const host=document.getElementById('platformSection');if(!host||document.getElementById('platformDenials'))return;
 const node=(tag,text)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;return n;};
 const box=node('section');box.id='platformDenials';
 const reload=node('button','Aktualisieren');reload.type='button';reload.className='secondary';
 const status=node('p','');status.setAttribute('role','status');const table=node('table');table.className='pu-table';
 box.append(node('h3','Abgelehnte Anfragen (zuletzt)'),node('small','Nur Methode, Adresse ohne Parameter, Benutzer und Grund – keine Inhalte. Gleiche Ablehnungen erscheinen höchstens einmal pro Minute; die Liste wird beim Neustart geleert.'),reload,status,table);
 host.append(box);
 async function load(){
  status.textContent='Lade …';
  try{
   const rows=await json('/api/platform/denials');table.replaceChildren();
   const head=node('tr');['Zeit','Status','Anfrage','Benutzer','Mandant','Grund'].forEach(h=>head.append(node('th',h)));table.append(head);
   rows.forEach(r=>{const tr=node('tr');
    [new Date(r.at).toLocaleTimeString('de-DE'),String(r.status),r.method+' '+r.path,r.user,r.tenant,r.reason+(r.repeats?' (+'+r.repeats+' gleiche)':'')].forEach(v=>tr.append(node('td',v)));table.append(tr);});
   status.textContent=rows.length?'':'Keine abgelehnten Anfragen seit dem letzten Start.';
  }catch(e){status.textContent=e.message;}
 }
 reload.onclick=load;load();
}
