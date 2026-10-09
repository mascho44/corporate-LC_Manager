const form=document.querySelector('#loginForm'),error=document.querySelector('#loginError'),button=form.querySelector('button');
let secondFactor=false,busy=false;
async function platformEnabled(){
  try{const response=await fetch('/api/platform/access');if(!response.ok)return false;return (await response.json()).enabled===true;}catch{return false;}
}
async function chooseWorkspace(){
  const response=await fetch('/api/tenants');
  const overview=await response.json().catch(()=>({}));
  const platform=await platformEnabled();
  if(!response.ok&&!platform)throw Error(overview.error||'Mandanten konnten nicht geladen werden.');
  const all=response.ok&&Array.isArray(overview.workspaces)?overview.workspaces:[];
  // The bootstrap tenant (code "default") only homes the accounts; offer it only when nothing else is available.
  const workspaces=all.length>1?all.filter(workspace=>workspace.code!=='default'):all;
  if(!workspaces.length){if(platform){location.replace('/platform.html');return;}throw Error('Kein berechtigter Mandant verfügbar.');}
  if(workspaces.length===1&&!platform){location.replace('/');return;}
  form.replaceChildren();
  const label=document.createElement('label');label.textContent='Mandant auswählen';
  const select=document.createElement('select');select.required=true;
  workspaces.forEach(workspace=>{const option=document.createElement('option');option.value=workspace.id;option.textContent=workspace.name;select.append(option);});
  select.value=workspaces.some(workspace=>workspace.id===overview.selectedTenantId)?overview.selectedTenantId:workspaces[0].id;label.append(select);
  const proceed=document.createElement('button');proceed.type='button';proceed.textContent='Mandant öffnen';
  form.append(label,error,proceed);
  if(platform){const console=document.createElement('button');console.type='button';console.className='secondary';console.textContent='Plattformverwaltung öffnen';console.onclick=()=>location.replace('/platform.html');form.append(console);}
  proceed.onclick=async()=>{if(proceed.disabled)return;proceed.disabled=true;error.textContent='';try{
    const meResponse=await fetch('/api/auth/me');const me=await meResponse.json();if(!meResponse.ok)throw Error('Anmeldung erforderlich.');
    const selected=await fetch('/api/tenants/'+encodeURIComponent(select.value)+'/select',{method:'POST',headers:{'X-CSRF-TOKEN':me.csrfToken}});
    if(!selected.ok){const body=await selected.json().catch(()=>({}));throw Error(body.error||'Mandant konnte nicht geöffnet werden.');}
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
    if(!response.ok)throw new Error(body.error||'Anmeldung fehlgeschlagen.');
    if(body.requiresTotp||body.requiresTotpSetup){
      secondFactor=true;
      form.querySelectorAll('label').forEach(label=>{label.hidden=true;label.querySelectorAll('input').forEach(input=>{input.required=false;input.disabled=true;input.value='';});});
      if(body.requiresTotpSetup){
        const setup=document.createElement('section');setup.className='totp-enrollment';
        const title=document.createElement('h2');title.textContent='Zwei-Faktor-Anmeldung einrichten';
        const hint=document.createElement('p');hint.textContent='Für Administratoren ist 2FA verpflichtend. Scannen Sie den QR-Code mit Ihrer Authenticator-App und bestätigen Sie den aktuellen Code.';
        const image=document.createElement('img');image.src=body.qrCodeDataUrl;image.alt='QR-Code für die Authenticator-App';image.width=220;image.height=220;
        const key=document.createElement('p');key.textContent='Manueller Einrichtungsschlüssel: '+body.secret;
        setup.append(title,hint,image,key);form.insertBefore(setup,error);
      }
      const label=document.createElement('label');
      label.innerHTML='Sicherheitscode<input name="code" inputmode="numeric" autocomplete="one-time-code" required placeholder="Code aus der Authenticator-App"><small>Bei bereits aktivierter 2FA können Sie auch einen Notfallcode verwenden.</small>';
      form.insertBefore(label,error);button.textContent='Code prüfen';label.querySelector('input').focus();return;
    }
    if(body.recoveryCodes){
      form.replaceChildren();
      const heading=document.createElement('h2');heading.textContent='Notfallcodes sicher aufbewahren';
      const hint=document.createElement('p');hint.textContent='Jeder Code kann einmal verwendet werden. Speichern Sie diese Codes jetzt an einem sicheren Ort.';
      const codes=document.createElement('pre');codes.textContent=body.recoveryCodes.join('\n');
      const proceed=document.createElement('button');proceed.type='button';proceed.textContent='Codes gesichert – weiter';proceed.onclick=()=>chooseWorkspace().catch(e=>{error.textContent=e.message;form.append(error);});
      form.append(heading,hint,codes,proceed);return;
    }
    await chooseWorkspace();
  }catch(e){error.textContent=e.message;}
  finally{busy=false;button.disabled=false;}
};
