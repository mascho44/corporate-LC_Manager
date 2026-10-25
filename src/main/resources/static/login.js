const form=document.querySelector('#loginForm'),error=document.querySelector('#loginError'),button=form.querySelector('button');
let secondFactor=false,busy=false;
async function platformAccess(){
  try{const response=await fetch('/api/platform/access');if(!response.ok)return {};return await response.json();}catch{return {};}
}
/** The home tenant only holds the accounts (the server marks it with home): offer it only when nothing else is available. */
function visibleWorkspaces(all){return all.length>1?all.filter(workspace=>workspace.home!==true):all;}
async function chooseWorkspace(){
  // A platform administrator belongs to no tenant: straight to the platform area, never a workspace chooser (a platform-only session could not even list workspaces).
  const access=await platformAccess();
  if(access.enabled===true){location.replace('/platform.html');return;}
  const response=await fetch('/api/tenants');
  const overview=await response.json().catch(()=>({}));
  const platform=access.enabled===true;
  if(!response.ok&&!platform)throw Error(overview.error||tx('login.tenantsFailed','Mandanten konnten nicht geladen werden.'));
  const all=response.ok&&Array.isArray(overview.workspaces)?overview.workspaces:[];
  // The home tenant only holds the accounts (the server marks it with home); offer it only when nothing else is available.
  const workspaces=visibleWorkspaces(all);
  if(!workspaces.length){if(platform){location.replace('/platform.html');return;}throw Error(tx('login.noTenant','Kein berechtigter Mandant verfügbar.'));}
  if(workspaces.length===1&&!platform){location.replace('/');return;}
  form.replaceChildren();
  const label=document.createElement('label');label.textContent=tx('login.chooseTenant','Mandant auswählen');
  const select=document.createElement('select');select.required=true;
  workspaces.forEach(workspace=>{const option=document.createElement('option');option.value=workspace.id;option.textContent=workspace.name;select.append(option);});
  select.value=workspaces.some(workspace=>workspace.id===overview.selectedTenantId)?overview.selectedTenantId:workspaces[0].id;label.append(select);
  const proceed=document.createElement('button');proceed.type='button';proceed.textContent=tx('login.openTenant','Mandant öffnen');
  form.append(label,error,proceed);
  if(platform){const console=document.createElement('button');console.type='button';console.className='secondary';console.textContent=tx('login.openPlatform','Plattformverwaltung öffnen');console.onclick=()=>location.replace('/platform.html');form.append(console);}
  proceed.onclick=async()=>{if(proceed.disabled)return;proceed.disabled=true;error.textContent='';try{
    const meResponse=await fetch('/api/auth/me');const me=await meResponse.json();if(!meResponse.ok)throw Error(tx('login.needLogin','Anmeldung erforderlich.'));
    const selected=await fetch('/api/tenants/'+encodeURIComponent(select.value)+'/select',{method:'POST',headers:{'X-CSRF-TOKEN':me.csrfToken}});
    if(!selected.ok){const body=await selected.json().catch(()=>({}));if(selected.status===403)throw Error(tx('login.tenantDenied','Der Zugriff auf diesen Mandanten wurde verweigert. Mögliche Ursache: Administratoren brauchen die Zwei-Faktor-Anmeldung. Bitte neu anmelden und sie einrichten.'));throw Error(body.error||tx('login.tenantFailed','Mandant konnte nicht geöffnet werden.'));}
    location.replace('/');
  }catch(e){error.textContent=e.message;}finally{proceed.disabled=false;}};
  form.onsubmit=event=>{event.preventDefault();return proceed.onclick();};
}
form.onsubmit=async event=>{
  event.preventDefault();if(busy)return;
  error.textContent='';busy=true;button.disabled=true;
  try{
    const response=await fetch(secondFactor?'/api/auth/login/totp':'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(secondFactor?{code:form.elements.code.value}:Object.fromEntries(new FormData(form).entries()))});
    const body=await response.json().catch(()=>({}));
    if(!response.ok)throw new Error(body.error||tx('login.failed','Anmeldung fehlgeschlagen.'));
    if(body.requiresTotp||body.requiresTotpSetup){
      secondFactor=true;
      form.querySelectorAll('label').forEach(label=>{label.hidden=true;label.querySelectorAll('input').forEach(input=>{input.required=false;input.disabled=true;input.value='';});});
      if(body.requiresTotpSetup){
        const setup=document.createElement('section');setup.className='totp-enrollment';
        const title=document.createElement('h2');title.textContent=tx('login.totpSetup','Zwei-Faktor-Anmeldung einrichten');
        const hint=document.createElement('p');hint.textContent=tx('login.totpSetupHint','Für Administratoren ist 2FA verpflichtend. Scannen Sie den QR-Code mit Ihrer Authenticator-App und bestätigen Sie den aktuellen Code.');
        const image=document.createElement('img');image.src=body.qrCodeDataUrl;image.alt=tx('login.qrAlt','QR-Code für die Authenticator-App');image.width=220;image.height=220;
        const key=document.createElement('p');key.textContent=tx('login.manualKey','Manueller Einrichtungsschlüssel: ')+body.secret;
        setup.append(title,hint,image,key);form.insertBefore(setup,error);
      }
      const label=document.createElement('label');
      const esc=value=>String(value).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));label.innerHTML=esc(tx('login.code','Sicherheitscode'))+'<input name="code" inputmode="numeric" autocomplete="one-time-code" required placeholder="'+esc(tx('login.codePlaceholder','Code aus der Authenticator-App'))+'"><small>'+esc(tx('login.recoveryHint','Bei bereits aktivierter 2FA können Sie auch einen Notfallcode verwenden.'))+'</small>';
      form.insertBefore(label,error);button.textContent=tx('login.checkCode','Code prüfen');label.querySelector('input').focus();return;
    }
    if(body.recoveryCodes){
      form.replaceChildren();
      const heading=document.createElement('h2');heading.textContent=tx('login.recoveryTitle','Notfallcodes sicher aufbewahren');
      const hint=document.createElement('p');hint.textContent=tx('login.recoveryText','Jeder Code kann einmal verwendet werden. Speichern Sie diese Codes jetzt an einem sicheren Ort.');
      const codes=document.createElement('pre');codes.textContent=body.recoveryCodes.join('\n');
      const proceed=document.createElement('button');proceed.type='button';proceed.textContent=tx('login.codesSaved','Codes gesichert – weiter');proceed.onclick=()=>chooseWorkspace().catch(e=>{error.textContent=e.message;form.append(error);});
      form.append(heading,hint,codes,proceed);return;
    }
    await chooseWorkspace();
  }catch(e){error.textContent=e.message;}
  finally{busy=false;button.disabled=false;}
};
