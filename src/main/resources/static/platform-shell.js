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
  document.getElementById('platformIdentity').textContent=currentUser.username;
  await globalThis.LcI18n?.setLanguage('en');
  await setupPlatformAdministration(true);
  const nav=document.getElementById('appNavPlatform');if(!nav)throw new Error('Platform access is unavailable.');await nav.onclick();buildSubnav();
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
