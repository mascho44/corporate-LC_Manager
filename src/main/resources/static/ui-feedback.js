// Shared feedback for long document uploads and accessible destructive confirmations.
function confirmAction(message) {
    return new Promise(resolve => {
        const previousFocus=document.activeElement;
        const dialog=document.createElement('dialog');
        dialog.className='feedback-dialog delete-confirmation';
        dialog.setAttribute('aria-labelledby','deleteConfirmationTitle');
        dialog.setAttribute('aria-describedby','deleteConfirmationMessage');
        dialog.innerHTML='<h2 id="deleteConfirmationTitle">Bitte bestätigen</h2><p id="deleteConfirmationMessage"></p><div class="feedback-actions"><button type="button" class="feedback-no" autofocus>Nein</button><button type="button" class="feedback-yes">Ja</button></div>';
        dialog.querySelector('p').textContent=message;
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
        dialog.innerHTML='<h2 id="processingTitle">Bitte warten</h2><p class="processing-stage" role="status" aria-live="polite">Dateien werden übertragen …</p><progress max="100" value="0" aria-label="Upload-Fortschritt"></progress><p class="processing-detail">Bitte dieses Fenster geöffnet lassen. Bei mehrseitigen Scans kann die Erkennung einige Minuten dauern.</p>';
        dialog.addEventListener('cancel',event=>event.preventDefault());
        document.body.append(dialog);dialog.showModal();
        const progress=dialog.querySelector('progress'),stage=dialog.querySelector('.processing-stage');
        const processing=()=>{progress.removeAttribute('value');progress.setAttribute('aria-label','Verarbeitung läuft');stage.textContent='Upload abgeschlossen. Dokumente werden ausgelesen, geprüft und gespeichert …';};
        const xhr=new XMLHttpRequest();
        const cleanup=()=>{options.signal?.removeEventListener('abort',abort);dialog.close();dialog.remove();previousFocus?.focus();};
        const abort=()=>xhr.abort();
        xhr.upload.addEventListener('progress',event=>{
            if(event.lengthComputable&&event.total>0){const percent=Math.round(event.loaded/event.total*100);progress.value=percent;stage.textContent=`Dateien werden übertragen: ${percent} %`;}
            else progress.removeAttribute('value');
        });
        xhr.upload.addEventListener('load',processing);
        xhr.onload=()=>{cleanup();resolve(new Response(xhr.status===204?null:xhr.responseText,{status:xhr.status,statusText:xhr.statusText,headers:{'Content-Type':xhr.getResponseHeader('Content-Type')||'application/json'}}));};
        xhr.onerror=()=>{cleanup();reject(new TypeError('Verbindung zum Server unterbrochen. Bitte den gespeicherten Stand prüfen, bevor erneut hochgeladen wird.'));};
        xhr.onabort=()=>{cleanup();reject(new DOMException('Upload abgebrochen','AbortError'));};
        try{
            xhr.open(options.method||'POST',url);
            new Headers(options.headers||{}).forEach((value,key)=>xhr.setRequestHeader(key,value));
            xhr.withCredentials=options.credentials==='include';
            options.signal?.addEventListener('abort',abort,{once:true});
            if(options.signal?.aborted){cleanup();reject(new DOMException('Upload abgebrochen','AbortError'));return;}
            xhr.send(options.body);
        }catch(error){cleanup();reject(error);}
    });
}
