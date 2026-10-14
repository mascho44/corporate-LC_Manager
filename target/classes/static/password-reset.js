(() => {
    const requestForm=document.querySelector('#resetRequestForm'),completeForm=document.querySelector('#resetCompleteForm'),message=document.querySelector('#resetMessage');
    let token=new URLSearchParams(location.hash.slice(1)).get('token');
    // Remove the secret from the address bar and browser history immediately. Never use storage.
    history.replaceState(null,'',location.pathname);
    if(token){requestForm.classList.add('hidden');completeForm.classList.remove('hidden');document.querySelector('#resetTitle').textContent='Neues Passwort setzen';}
    async function submit(form,path,payload){
        const button=form.querySelector('button');button.disabled=true;message.textContent='';
        try{
            const response=await fetch('/api/auth/password-reset/'+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload),cache:'no-store'});
            const body=await response.json().catch(()=>({}));
            if(!response.ok)throw new Error(body.error||'Die Anfrage konnte nicht verarbeitet werden.');
            message.textContent=body.message;
            if(path==='complete'){token=null;form.reset();form.classList.add('hidden');}
            else form.reset();
        }catch(error){message.textContent=error.message;}
        finally{button.disabled=false;}
    }
    requestForm.onsubmit=event=>{event.preventDefault();submit(requestForm,'request',{username:requestForm.elements.username.value.trim(),email:requestForm.elements.email.value.trim()});};
    completeForm.onsubmit=event=>{
        event.preventDefault();
        if(completeForm.elements.password.value!==completeForm.elements.confirmation.value){message.textContent='Die Passwörter stimmen nicht überein.';return;}
        submit(completeForm,'complete',{token,password:completeForm.elements.password.value});
    };
})();
