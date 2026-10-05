const form=document.querySelector('#loginForm'),error=document.querySelector('#loginError'),button=form.querySelector('button');
let secondFactor=false,busy=false;
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
      const proceed=document.createElement('button');proceed.type='button';proceed.textContent='Codes gesichert – weiter';proceed.onclick=()=>location.replace('/');
      form.append(heading,hint,codes,proceed);return;
    }
    location.replace('/');
  }catch(e){error.textContent=e.message;}
  finally{busy=false;button.disabled=false;}
};
