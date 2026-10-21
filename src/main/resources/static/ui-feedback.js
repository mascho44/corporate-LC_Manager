// Shared feedback for long document uploads and accessible destructive confirmations.
function confirmAction(message, labels={}) {
    return new Promise(resolve => {
        const previousFocus=document.activeElement;
        const dialog=document.createElement('dialog');
        dialog.className='feedback-dialog delete-confirmation';
        dialog.setAttribute('aria-labelledby','deleteConfirmationTitle');
        dialog.setAttribute('aria-describedby','deleteConfirmationMessage');
        dialog.innerHTML='<h2 id="deleteConfirmationTitle">'+tx('ufh.acd4cc','Bitte bestätigen')+'</h2><p id="deleteConfirmationMessage"></p><div class="feedback-actions"><button type="button" class="feedback-no" autofocus>'+tx('ufh.b397ec','Nein')+'</button><button type="button" class="feedback-yes">Ja</button></div>';
        dialog.querySelector('p').textContent=message;
        if(labels.title)dialog.querySelector('h2').textContent=labels.title;
        if(labels.cancel)dialog.querySelector('.feedback-no').textContent=labels.cancel;
        if(labels.confirm)dialog.querySelector('.feedback-yes').textContent=labels.confirm;
        let settled=false;
        const finish=value=>{if(settled)return;settled=true;dialog.close();dialog.remove();previousFocus?.focus();resolve(value);};
        dialog.querySelector('.feedback-no').onclick=()=>finish(false);
        dialog.querySelector('.feedback-yes').onclick=()=>finish(true);
        dialog.addEventListener('cancel',event=>{event.preventDefault();finish(false);});
        dialog.addEventListener('close',()=>finish(false));
        document.body.append(dialog);dialog.showModal();
    });
}

function uploadWithProgress(url,options={}) {
    return new Promise((resolve,reject)=>{
        const previousFocus=document.activeElement;
        const dialog=document.createElement('dialog');
        dialog.className='feedback-dialog processing-dialog';
        dialog.setAttribute('aria-labelledby','processingTitle');
        dialog.setAttribute('aria-busy','true');
        dialog.innerHTML='<h2 id="processingTitle">'+tx('ufh.5a328a','Bitte warten')+'</h2><p class="processing-stage" role="status" aria-live="polite">'+tx('ufh.18ba1c','Dateien werden übertragen …')+'</p><progress max="100" value="0" aria-label="'+tx('ufh.de46a9','Upload-Fortschritt')+'"></progress><p class="processing-detail">'+tx('ufh.984438','Bitte dieses Fenster geöffnet lassen. Bei mehrseitigen Scans kann die Erkennung einige Minuten dauern.')+'</p>';
        dialog.addEventListener('cancel',event=>event.preventDefault());
        document.body.append(dialog);dialog.showModal();
        const progress=dialog.querySelector('progress'),stage=dialog.querySelector('.processing-stage');
        const processing=()=>{progress.removeAttribute('value');progress.setAttribute('aria-label',tx('ufl.0c3ae1','Verarbeitung läuft'));stage.textContent=url==='/api/inbox'?tx('ufl.229c86','Upload abgeschlossen. Dateien werden sicher gespeichert; die Erkennung läuft anschließend im Hintergrund.'):tx('ufl.7eaa5c','Upload abgeschlossen. Dokumente werden ausgelesen, geprüft und gespeichert …');};
        const xhr=new XMLHttpRequest();
        const cleanup=()=>{options.signal?.removeEventListener('abort',abort);dialog.close();dialog.remove();previousFocus?.focus();};
        const abort=()=>xhr.abort();
        xhr.upload.addEventListener('progress',event=>{
            if(event.lengthComputable&&event.total>0){const percent=Math.round(event.loaded/event.total*100);progress.value=percent;stage.textContent=tx('uft.9d5278','Dateien werden übertragen: {p0} %',{p0:percent});}
            else progress.removeAttribute('value');
        });
        xhr.upload.addEventListener('load',processing);
        xhr.onload=()=>{cleanup();resolve(new Response(xhr.status===204?null:xhr.responseText,{status:xhr.status,statusText:xhr.statusText,headers:{'Content-Type':xhr.getResponseHeader('Content-Type')||'application/json'}}));};
        xhr.onerror=()=>{cleanup();reject(new TypeError(tx('ufl.0cbc2e','Verbindung zum Server unterbrochen. Bitte den gespeicherten Stand prüfen, bevor erneut hochgeladen wird.')));};
        xhr.onabort=()=>{cleanup();reject(new DOMException(tx('ufl.e62690','Upload abgebrochen'),'AbortError'));};
        try{
            xhr.open(options.method||'POST',url);
            new Headers(options.headers||{}).forEach((value,key)=>xhr.setRequestHeader(key,value));
            xhr.withCredentials=options.credentials==='include';
            options.signal?.addEventListener('abort',abort,{once:true});
            if(options.signal?.aborted){cleanup();reject(new DOMException(tx('ufl.e62690','Upload abgebrochen'),'AbortError'));return;}
            xhr.send(options.body);
        }catch(error){cleanup();reject(error);}
    });
}
