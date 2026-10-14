(() => {
 const form=document.querySelector('#invitationForm'),message=document.querySelector('#invitationMessage');
 let token=new URLSearchParams(location.hash.slice(1)).get('token');history.replaceState(null,'',location.pathname);
 if(!token||!token.match(/^[A-Za-z0-9_-]{43}$/)){token=null;form.hidden=true;message.textContent='Invitation is invalid or missing. Request a new invitation from your administrator.';return;}
 let busy=false;
 form.onsubmit=async event=>{event.preventDefault();if(busy||!form.reportValidity())return;message.textContent='';
  if(form.elements.password.value!==form.elements.confirmation.value){message.textContent='Passwords do not match.';return;}
  busy=true;const button=form.querySelector('button');button.disabled=true;
  try{const response=await fetch('/api/auth/invitation/accept',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token,password:form.elements.password.value}),cache:'no-store'});const body=await response.json().catch(()=>({}));if(!response.ok)throw Error(body.error||'Invitation could not be accepted.');message.textContent=body.message;token=null;form.hidden=true;}
  catch(error){message.textContent=error.message;}finally{form.reset();button.disabled=false;busy=false;}
 };
})();
