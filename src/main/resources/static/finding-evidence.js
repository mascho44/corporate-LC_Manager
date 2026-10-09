async function openFindingEvidence(button){
    const lcId=activeLc?.id;if(!lcId)return;
    let dialog=document.querySelector('#findingEvidenceDialog');
    if(!dialog){dialog=document.createElement('dialog');dialog.id='findingEvidenceDialog';dialog.className='wide-dialog';document.body.append(dialog);}
    dialog.onclose?.();dialog.onclose=null;
    dialog.innerHTML='<p>Belegansicht wird geladen …</p><button type="button" data-evidence-close>Schließen</button>';
    dialog.querySelector('button').onclick=()=>dialog.close();dialog.showModal();
    try{
        const params=new URLSearchParams({code:button.dataset.findingEvidence});if(button.dataset.findingDocument)params.set('documentName',button.dataset.findingDocument);if(button.dataset.findingFingerprint)params.set('reviewFingerprint',button.dataset.findingFingerprint);
        if(button.dataset.findingMode==='PRECHECK')params.set('mode','PRECHECK');
        const view=await json(`/api/lcs/${lcId}/document-checks/evidence?${params}`);
        if(!dialog.open)return;
        const finding=view.finding,location=view.location,page=location.status==='MATCH'?location.pages[0]:null;
        const url=view.documentId?`/api/documents/${encodeURIComponent(view.documentId)}/preview${page?'#page='+page:''}`:null;
        const status=location.status==='MATCH'?`${location.method.includes('VALUE_ANCHOR')?'Wertanker (kein exaktes Zitat)':'Eindeutiger Textbeleg'} auf Seite ${page}`:location.status==='AMBIGUOUS'?`Mehrere Fundstellen (Seiten ${location.pages.join(', ')}). Bitte visuell zuordnen.`:location.status==='AMBIGUOUS_DOCUMENT'?'Mehrere Dokumente haben denselben Namen. Keine automatische Zuordnung.':'Keine eindeutige Seitenfundstelle. Bitte das Original fachlich prüfen.';
        dialog.innerHTML=`<div class="dialoghead"><h2>${view.mode==='PRECHECK'?'Vorprüfung – Belegansicht':'Fachliche Befundprüfung'}</h2><button type="button" data-evidence-close>Schließen</button></div><div class="evidence-workspace"><section class="evidence-details"><h3>LC-Bedingung</h3><p>${esc(finding.lcCondition||'Keine konkrete Bedingung hinterlegt.')}</p><h3>Automatischer Befund</h3><p>${esc(label(finding.automaticSeverity))} · ${esc(finding.message)}</p>${renderRuleMetadata(finding)}<h3>Dokumentbeleg</h3><b>${esc(finding.documentName||'Kein einzelnes Dokument')}</b><blockquote>${esc(finding.documentEvidence||'Kein Textbeleg gespeichert.')}</blockquote><p role="status">${esc(status)}</p><small>Seitensuche: ${esc(location.method)}. Kein Nachweis fachlicher Richtigkeit.</small><h3>Fachliche Entscheidung</h3><p>${finding.reviewDecision?esc(finding.reviewDecision==='ACCEPTED'?'Als erfüllt bestätigt':'Abweichung bestätigt')+' · '+esc(finding.reviewedBy)+' · '+esc(finding.reviewComment||''):'Noch nicht entschieden.'}</p>${view.mode!=='PRECHECK'&&can('DOCUMENT_REVIEW')?'<button type="button" data-evidence-decision="ACCEPTED">Als erfüllt bestätigen</button> <button type="button" class="danger" data-evidence-decision="CONFIRMED_DISCREPANCY">Abweichung bestätigen</button>':''}<p>${view.mode==='PRECHECK'?'Nur Vorprüfung: keine Entscheidungen oder Freigabe.':'Eine Befundentscheidung ist keine Endprüfung oder Vier-Augen-Freigabe.'}</p></section><section class="evidence-document">${url?`<a class="button-link secondary" target="_blank" rel="noopener" href="${url}">Original separat öffnen</a>${view.contentType==='application/pdf'?`<div data-pdf-pages></div>`:/^image\/(png|jpeg)$/.test(view.contentType)?`<img alt="Originaldokument" src="${url}">`:'<p>Vorschau für diesen Dateityp nicht verfügbar.</p>'}`:'<p>Kein eindeutig zugeordnetes Original. Bitte in der Dokumentenakte auswählen.</p>'}</section></div>`;
        dialog.querySelector('[data-evidence-close]').onclick=()=>dialog.close();
        dialog.querySelectorAll('[data-evidence-decision]').forEach(action=>action.onclick=async()=>{if(activeLc?.id!==lcId){alert('Die aktive Akte hat sich geändert. Belegansicht bitte erneut öffnen.');return;}dialog.close();await decideCheck(finding.code,finding.documentName||'',action.dataset.evidenceDecision,finding.reviewFingerprint);});
        const pdfPages=dialog.querySelector('[data-pdf-pages]');
        const cropKind=/SIGNATURE/.test(finding.code)?'signature':/DATE/.test(finding.code)?'date':null;
        if(cropKind&&view.documentId&&view.contentType==='application/pdf'&&pdfPages)showFindingCrop(dialog,pdfPages,lcId,view.documentId,cropKind);
        if(view.documentId&&view.contentType==='application/pdf'&&pdfPages)await renderFindingPdf(dialog,pdfPages,view.documentId,page||1);
    }catch(error){dialog.innerHTML=`<p class="error">${esc(error.message)}</p><button type="button">Schließen</button>`;dialog.querySelector('button').onclick=()=>dialog.close();}
}
async function showFindingCrop(dialog,container,lcId,documentId,kind){
    try{
        const response=await fetch(`/api/lcs/${lcId}/documents/${encodeURIComponent(documentId)}/finding-crop?kind=${kind}`);
        if(!response.ok||!dialog.open)return;
        const url=URL.createObjectURL(await response.blob());
        const figure=document.createElement('figure');figure.className='finding-crop';
        const image=document.createElement('img');image.src=url;image.alt=kind==='signature'?'Ausschnitt Unterschriftsbereich':'Ausschnitt Datum';
        image.onload=()=>URL.revokeObjectURL(url);
        const caption=document.createElement('figcaption');caption.textContent='Ausschnitt der betroffenen Stelle (orange markiert, automatisch erkannt – bitte prüfen)';
        figure.append(image,caption);container.before(figure);
    }catch(ignored){}
}
async function renderFindingPdf(dialog,container,documentId,initialPage){
    container.innerHTML='<div class="evidence-page-toolbar"><button type="button" data-prev disabled aria-label="Vorherige Seite">←</button><span data-page></span><button type="button" data-next disabled aria-label="Nächste Seite">→</button><button type="button" data-zoom>Vergrößern</button><button type="button" data-retry>Neu laden</button></div><p data-status role="status"></p><div class="evidence-document-viewport"><img alt="PDF-Seite" hidden></div>';
    const image=container.querySelector('img'),status=container.querySelector('[data-status]'),previous=container.querySelector('[data-prev]'),next=container.querySelector('[data-next]'),retry=container.querySelector('[data-retry]'),pageLabel=container.querySelector('[data-page]');
    const abort=new AbortController();let imageUrl=null,selectedPage=initialPage,pageCount=null,version=0,loading=false;
    const oldClose=dialog.onclose;dialog.onclose=()=>{abort.abort();if(imageUrl)URL.revokeObjectURL(imageUrl);oldClose?.();};
    const controls=()=>{previous.disabled=loading||selectedPage<=1;next.disabled=loading||pageCount==null||selectedPage>=pageCount;retry.disabled=loading;pageLabel.textContent=`Seite ${selectedPage}${pageCount?' / '+pageCount:''}`;};
    async function loadPage(number){
        const request=++version;selectedPage=number;loading=true;controls();image.hidden=true;status.textContent=`Seite ${number} wird geladen …`;
        try{
            const response=await fetch(`/api/documents/${encodeURIComponent(documentId)}/pages/${number}/preview`,{signal:abort.signal});
            if(!response.ok||!response.headers.get('Content-Type')?.startsWith('image/png'))throw Error(`Seitenvorschau nicht verfügbar (HTTP ${response.status}). Bitte neu laden oder das Original separat öffnen.`);
            const blob=await response.blob();if(request!==version||!dialog.open)return;
            const count=Number(response.headers.get('X-Page-Count'));if(Number.isInteger(count)&&count>=number)pageCount=count;
            if(imageUrl)URL.revokeObjectURL(imageUrl);imageUrl=URL.createObjectURL(blob);
            image.onload=()=>{if(request===version&&dialog.open){image.hidden=false;status.textContent='';}};
            image.onerror=()=>{if(request===version&&dialog.open)status.textContent='Seitenbild konnte nicht angezeigt werden. Bitte neu laden oder das Original separat öffnen.';};
            image.alt=`PDF-Seite ${number}`;image.src=imageUrl;
        }catch(error){if(request===version&&!abort.signal.aborted)status.textContent=error.message;}
        finally{if(request===version){loading=false;controls();}}
    }
    previous.onclick=()=>loadPage(selectedPage-1);next.onclick=()=>loadPage(selectedPage+1);retry.onclick=()=>loadPage(selectedPage);
    container.querySelector('[data-zoom]').onclick=event=>{const zoomed=image.classList.toggle('zoomed');event.target.textContent=zoomed?'Einpassen':'Vergrößern';};
    await loadPage(selectedPage);
}
document.addEventListener('click',event=>{const button=event.target.closest('[data-finding-evidence]');if(button)openFindingEvidence(button);});
