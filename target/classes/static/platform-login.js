const form=document.querySelector('#platformLoginForm'),error=document.querySelector('#platformLoginError'),button=form.querySelector('button');
let secondFactor=false,busy=false;
async function post(url,body){
  const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  const data=await response.json().catch(()=>({}));
  if(!response.ok)throw new Error(data.error||'Anmeldung fehlgeschlagen.');
  return data;
}
async function ensurePlatformAccess(){
  const response=await fetch('/api/platform/access');
  const access=response.ok?await response.json().catch(()=>({})):{};
  if(access.enabled===true){location.replace('/platform.html');return;}
  try{const me=await (await fetch('/api/auth/me')).json();await fetch('/api/auth/logout',{method:'POST',headers:{'X-CSRF-TOKEN':me.csrfToken}});}catch{}
  throw new Error('Dieses Konto hat keinen Plattformzugang oder keine aktive Zwei-Faktor-Anmeldung.');
}
form.onsubmit=async event=>{
  event.preventDefault();if(busy)return;error.textContent='';busy=true;button.disabled=true;
  try{
    const body=await post(secondFactor?'/api/auth/login/totp':'/api/auth/login',secondFactor?{code:form.elements.code.value}:Object.fromEntries(new FormData(form).entries()));
    if(body.requiresTotpSetup)throw new Error('Bitte zuerst die Zwei-Faktor-Anmeldung über die normale Anmeldung einrichten.');
    if(body.requiresTotp){
      secondFactor=true;
      form.querySelectorAll('label').forEach(label=>{label.hidden=true;label.querySelectorAll('input').forEach(input=>{input.required=false;input.disabled=true;input.value='';});});
      const label=document.createElement('label');label.textContent='Sicherheitscode';
      const input=document.createElement('input');input.name='code';input.inputMode='numeric';input.autocomplete='one-time-code';input.required=true;input.placeholder='Code aus der Authenticator-App';
      label.append(input);form.insertBefore(label,error);button.textContent='Code prüfen';input.focus();return;
    }
    await ensurePlatformAccess();
  }catch(failure){error.textContent=failure.message;}
  finally{busy=false;button.disabled=false;}
};
