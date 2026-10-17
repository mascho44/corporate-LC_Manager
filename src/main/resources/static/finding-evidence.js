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
        dialog.innerHTML=`<div class="dialoghead"><h2>${view.mode==='PRECHECK'?'Vorprüfung – Belegansicht':'Fachliche Befundprüfung'}</h2><button type="button" data-evidence-close>Schließen</button></div><div class="evidence-workspace"><section class="evidence-details"><h3>LC-Bedingung</h3><p>${esc(finding.lcCondition||'Keine konkrete Bedingung hinterlegt.')}</p><h3>Automatischer Befund</h3><p>${esc(label(finding.automaticSeverity))} · ${esc(finding.message)}</p>${renderRuleMetadata(finding)}<h3>Dokumentbeleg</h3><b>${esc(finding.documentName||'Kein einzelnes Dokument')}</b><blockquote>${esc((globalThis.readableEvidence?readableEvidence(finding.documentEvidence):finding.documentEvidence)||'Kein Textbeleg gespeichert.')}</blockquote><p role="status">${esc(status)}</p><small>Seitensuche: ${esc(location.method)}. Kein Nachweis fachlicher Richtigkeit.</small><h3>Fachliche Entscheidung</h3><p>${finding.reviewDecision?esc(finding.reviewDecision==='ACCEPTED'?'Als erfüllt bestätigt':'Abweichung bestätigt')+' · '+esc(finding.reviewedBy)+' · '+esc(finding.reviewComment||''):'Noch nicht entschieden.'}</p>${view.mode!=='PRECHECK'&&can('DOCUMENT_REVIEW')?'<button type="button" data-evidence-decision="ACCEPTED">Als erfüllt bestätigen</button> <button type="button" class="danger" data-evidence-decision="CONFIRMED_DISCREPANCY">Abweichung bestätigen</button>':''}<p>${view.mode==='PRECHECK'?'Nur Vorprüfung: keine Entscheidungen oder Freigabe.':'Eine Befundentscheidung ist keine Endprüfung oder Vier-Augen-Freigabe.'}</p></section><section class="evidence-document">${url?`<a class="button-link secondary" target="_blank" rel="noopener" href="${url}">Original separat öffnen</a>${view.contentType==='application/pdf'?`<div data-pdf-pages></div>`:/^image\/(png|jpeg)$/.test(view.contentType)?`<img alt="Originaldokument" src="${url}">`:'<p>Vorschau für diesen Dateityp nicht verfügbar.</p>'}`:'<p>Kein eindeutig zugeordnetes Original. Bitte in der Dokumentenakte auswählen.</p>'}</section></div>`;
        dialog.querySelector('[data-evidence-close]').onclick=()=>dialog.close();
        dialog.querySelectorAll('[data-evidence-decision]').forEach(action=>action.onclick=async()=>{if(activeLc?.id!==lcId){alert('Die aktive Akte hat sich geändert. Belegansicht bitte erneut öffnen.');return;}const mark=typeof dialog.getEvidenceMark==='function'?dialog.getEvidenceMark():null;dialog.close();await decideCheck(finding.code,finding.documentName||'',action.dataset.evidenceDecision,finding.reviewFingerprint,mark);});
        const pdfPages=dialog.querySelector('[data-pdf-pages]');
        const cropKind=/SIGNATURE/.test(finding.code)?'signature':/ORIGINAL|COPY|STAMP/.test(finding.code)?'stamp':/DATE/.test(finding.code)?'date':null;
        if(cropKind&&view.documentId&&view.contentType==='application/pdf'&&pdfPages)showFindingCrop(dialog,pdfPages,lcId,view.documentId,cropKind);
        if(view.documentId&&view.contentType==='application/pdf'&&pdfPages)await renderFindingPdf(dialog,pdfPages,view.documentId,page||1,location.status==='MATCH'||location.status==='AMBIGUOUS'?location.pages:[],parseMark(view.evidenceMark));dialog.showEvidenceLines?.(location.boxes);
    }catch(error){dialog.innerHTML=`<p class="error">${esc(error.message)}</p><button type="button">Schließen</button>`;dialog.querySelector('button').onclick=()=>dialog.close();}
}
async function showFindingCrop(dialog,container,lcId,documentId,kind){
    try{
        const response=await fetch(`/api/lcs/${lcId}/documents/${encodeURIComponent(documentId)}/finding-crop?kind=${kind}`);
        if(!response.ok||!dialog.open)return;
        const url=URL.createObjectURL(await response.blob());
        const figure=document.createElement('figure');figure.className='finding-crop';
        const image=document.createElement('img');image.src=url;image.alt=kind==='signature'?'Ausschnitt Unterschriftsbereich':kind==='stamp'?'Ausschnitt Stempel':'Ausschnitt Datum';
        image.onload=()=>URL.revokeObjectURL(url);
        const caption=document.createElement('figcaption');caption.textContent='Ausschnitt der betroffenen Stelle (orange markiert, automatisch erkannt – bitte prüfen)';
        figure.append(image,caption);container.before(figure);
    }catch(ignored){}
}
/** The stored mark of a decision (JSON text from the server) or null. */
function parseMark(text){try{const m=JSON.parse(text||'null');return m&&typeof m.documentId==='string'&&Number.isInteger(m.page)&&[m.x,m.y,m.w,m.h].every(v=>typeof v==='number')?m:null;}catch(ignore){return null;}}
async function renderFindingPdf(dialog,container,documentId,initialPage,markedPages=[],savedMark=null){
    container.innerHTML='<div class="evidence-page-toolbar"><button type="button" data-prev disabled aria-label="Vorherige Seite">←</button><span data-page></span><button type="button" data-next disabled aria-label="Nächste Seite">→</button><button type="button" data-zoom>Vergrößern</button><button type="button" data-retry>Neu laden</button><button type="button" data-mark title="Rechteck um die Fundstelle ziehen; ersetzt den Kommentar bei der Bestätigung">Fundstelle markieren</button><button type="button" data-mark-clear hidden>Markierung entfernen</button></div><p data-status role="status"></p><div class="evidence-viewer"><nav class="evidence-pages" aria-label="Seiten" hidden></nav><div class="evidence-document-viewport"><div class="evidence-page-wrap"><img alt="PDF-Seite" hidden></div></div></div>';
    const image=container.querySelector('img'),status=container.querySelector('[data-status]'),previous=container.querySelector('[data-prev]'),next=container.querySelector('[data-next]'),retry=container.querySelector('[data-retry]'),pageLabel=container.querySelector('[data-page]');
    const abort=new AbortController();let imageUrl=null,selectedPage=initialPage,pageCount=null,version=0,loading=false;
    // Marking the evidence: a rectangle on the shown page (fractions of the page); it replaces the written comment when a finding is confirmed manually.
    const wrap=container.querySelector('.evidence-page-wrap'),markButton=container.querySelector('[data-mark]'),clearButton=container.querySelector('[data-mark-clear]');
    // Automatically found lines of the evidence (yellow, from the server); a help for the eye, not a decision.
    // Checked positions: a small red tick (accepted) or cross (confirmed discrepancy) next to the place a decision refers to, as on a paper check.
    let checkMarks=[];const checkLayer=wrap&&typeof document!=='undefined'?document.createElement('div'):null;
    if(checkLayer){checkLayer.className='evidence-checks';checkLayer.setAttribute('aria-hidden','true');}
    const refreshChecks=()=>{if(!checkLayer?.replaceChildren)return;checkLayer.replaceChildren(...(image.hidden?[]:checkMarks.filter(m=>m.page===selectedPage).map(m=>{const d=document.createElement('div');d.className='evidence-check'+(m.kind==='discrepancy'?' bad':'');d.textContent=m.kind==='discrepancy'?'✗':'✓';d.style.left=Math.min(97,(m.x+m.w)*100)+'%';d.style.top=(m.y+m.h/2)*100+'%';if(m.title)d.title=m.title;return d;})));};
    dialog.setCheckmarks=list=>{checkMarks=Array.isArray(list)?list.filter(m=>Number.isInteger(m?.page)&&[m.x,m.y,m.w,m.h].every(v=>typeof v==='number')):[];refreshChecks();};
    let lineHints=[];const hintLayer=wrap&&typeof document!=='undefined'?document.createElement('div'):null;
    if(hintLayer){hintLayer.className='evidence-line-hints';hintLayer.setAttribute('aria-hidden','true');}
    const refreshHints=()=>{if(!hintLayer?.replaceChildren)return;hintLayer.replaceChildren(...(image.hidden?[]:lineHints.filter(b=>b.page===selectedPage).map(b=>{const d=document.createElement('div');d.className='evidence-line-hint';d.style.left=b.x*100+'%';d.style.top=b.y*100+'%';d.style.width=b.w*100+'%';d.style.height=b.h*100+'%';return d;})));};
    dialog.showEvidenceLines=boxes=>{lineHints=Array.isArray(boxes)?boxes.filter(b=>Number.isInteger(b?.page)&&[b.x,b.y,b.w,b.h].every(v=>typeof v==='number')):[];refreshHints();const first=lineHints.find(b=>b.page===selectedPage);if(first&&!image.hidden)hintLayer?.firstElementChild?.scrollIntoView?.({block:'center',behavior:'smooth'});};
    let refreshMark=()=>{},current=null;const saved=savedMark&&savedMark.documentId===documentId?savedMark:null;
    if(wrap&&markButton&&clearButton&&typeof document!=='undefined'){
    const box=document.createElement('div');box.className='evidence-mark-box';box.hidden=true;box.setAttribute('aria-hidden','true');
    const capture=document.createElement('div');capture.className='evidence-mark-capture';capture.hidden=true;wrap.append(box,capture);
    const place=m=>{box.style.left=m.x*100+'%';box.style.top=m.y*100+'%';box.style.width=m.w*100+'%';box.style.height=m.h*100+'%';};
    refreshMark=()=>{
        const shown=current||saved;
        if(shown&&shown.page===selectedPage&&!image.hidden){place(shown);box.hidden=false;box.classList.toggle('saved',!current);}else box.hidden=true;
        clearButton.hidden=!current;markButton.textContent=current?'Fundstelle neu markieren':'Fundstelle markieren';
    };
    dialog.clearEvidenceMark=()=>{current=null;refreshMark();};
    dialog.getEvidenceMark=()=>current?{documentId,page:current.page,x:current.x,y:current.y,w:current.w,h:current.h}:null;
    markButton.onclick=()=>{capture.hidden=!capture.hidden;status.textContent=capture.hidden?'':'Mit gedrückter Maustaste (oder dem Finger) ein Rechteck um die Fundstelle ziehen.';};
    clearButton.onclick=()=>{current=null;refreshMark();};
    let start=null;
    const fraction=event=>{const r=wrap.getBoundingClientRect();return{x:Math.min(1,Math.max(0,(event.clientX-r.left)/r.width)),y:Math.min(1,Math.max(0,(event.clientY-r.top)/r.height))};};
    capture.onpointerdown=event=>{event.preventDefault();start=fraction(event);capture.setPointerCapture?.(event.pointerId);place({x:start.x,y:start.y,w:0,h:0});box.hidden=false;box.classList.remove('saved');};
    capture.onpointermove=event=>{if(!start)return;const p=fraction(event);place({x:Math.min(start.x,p.x),y:Math.min(start.y,p.y),w:Math.abs(p.x-start.x),h:Math.abs(p.y-start.y)});};
    capture.onpointerup=event=>{
        if(!start)return;const p=fraction(event),m={page:selectedPage,x:Math.min(start.x,p.x),y:Math.min(start.y,p.y),w:Math.abs(p.x-start.x),h:Math.abs(p.y-start.y)};start=null;
        if(m.w>=0.01&&m.h>=0.005){current=m;capture.hidden=true;status.textContent='Fundstelle markiert. Jetzt „Als erfüllt bestätigen" – ein Kommentar ist dann nicht nötig.';}
        refreshMark();
    };
    }
    if(hintLayer)wrap.append(hintLayer);if(checkLayer)wrap.append(checkLayer);
    const sidebar=container.querySelector('.evidence-pages');let sidebarBuilt=false;
    const keyHandler=event=>{
        if(!dialog.open||event.altKey||event.ctrlKey||event.metaKey||/^(INPUT|TEXTAREA|SELECT)$/.test(event.target?.tagName||''))return;
        const step={ArrowLeft:-1,PageUp:-1,ArrowRight:1,PageDown:1}[event.key];
        const target=step?selectedPage+step:event.key==='Home'?1:event.key==='End'&&pageCount?pageCount:null;
        if(target==null||loading||target<1||(pageCount&&target>pageCount)||target===selectedPage)return;
        event.preventDefault();return loadPage(target);
    };
    dialog.addEventListener?.('keydown',keyHandler);
    const dispose=()=>{dialog.setCheckmarks=undefined;abort.abort();dialog.removeEventListener?.('keydown',keyHandler);if(imageUrl)URL.revokeObjectURL(imageUrl);};
    // A caller that replaces the viewer (document-wise review) disposes the old one first; closing the dialog does it too.
    dialog.disposePdfViewer=dispose;
    const oldClose=dialog.onclose;dialog.onclose=()=>{dispose();oldClose?.();};
    const marked=new Set(markedPages);
    function buildSidebar(){
        if(!sidebar||sidebarBuilt||!pageCount||pageCount<2||typeof document==='undefined')return;
        sidebarBuilt=true;sidebar.hidden=false;
        const lazy=typeof IntersectionObserver==='function'?new IntersectionObserver(entries=>entries.forEach(entry=>{if(entry.isIntersecting){lazy.unobserve(entry.target);entry.target.src=entry.target.dataset.src;}}),{root:sidebar,rootMargin:'200px'}):null;
        const mark=(n,count,severity)=>{const b=sidebar.querySelector(`.evidence-page-thumb[data-page="${n}"]`);if(!b||marked.has(n))return;b.classList.add('has-other',severity==='DISCREPANCY'?'other-bad':'other-warn');b.title=`${count} offene${count===1?'r':''} Befund${count===1?'':'e'} auf dieser Seite`;b.setAttribute('aria-label',`Seite ${n} – ${b.title}`);};
        for(let n=1;n<=Math.min(pageCount,100);n++){
            const button=document.createElement('button');button.type='button';button.className='evidence-page-thumb';button.dataset.page=n;
            button.setAttribute('aria-label',`Seite ${n}${marked.has(n)?' – hier liegt der Befund':''}`);
            if(marked.has(n)){button.classList.add('has-finding');button.title='Hier liegt der Befund';}
            const img=document.createElement('img');img.alt='';img.decoding='async';img.dataset.src=`/api/documents/${encodeURIComponent(documentId)}/pages/${n}/preview?size=thumb`;
            const label=document.createElement('span');label.textContent=String(n);
            button.append(img,label);button.onclick=()=>(!loading&&n!==selectedPage)?loadPage(n):undefined;sidebar.append(button);
            if(lazy)lazy.observe(img);else img.src=img.dataset.src;
        }
        const lcId=typeof activeLc!=='undefined'?activeLc?.id:null;
        if(lcId)fetch(`/api/lcs/${encodeURIComponent(lcId)}/documents/${encodeURIComponent(documentId)}/finding-pages`,{signal:abort.signal}).then(r=>r.ok?r.json():[]).then(list=>{if(dialog.open&&Array.isArray(list))list.forEach(m=>mark(m.page,m.count,m.severity));}).catch(()=>{});
    }
    function markSelected(){
        if(!sidebar||!sidebarBuilt)return;
        sidebar.querySelectorAll('.evidence-page-thumb').forEach(button=>{const on=Number(button.dataset.page)===selectedPage;button.classList.toggle('selected',on);button.setAttribute('aria-current',on?'page':'false');if(on)button.scrollIntoView?.({block:'nearest'});});
    }
    const controls=()=>{previous.disabled=loading||selectedPage<=1;next.disabled=loading||pageCount==null||selectedPage>=pageCount;retry.disabled=loading;pageLabel.textContent=`Seite ${selectedPage}${pageCount?' / '+pageCount:''}`;};
    async function loadPage(number){
        const request=++version;selectedPage=number;loading=true;controls();image.hidden=true;refreshHints();refreshChecks();status.textContent=`Seite ${number} wird geladen …`;
        try{
            const response=await fetch(`/api/documents/${encodeURIComponent(documentId)}/pages/${number}/preview`,{signal:abort.signal});
            if(!response.ok||!response.headers.get('Content-Type')?.startsWith('image/png'))throw Error(`Seitenvorschau nicht verfügbar (HTTP ${response.status}). Bitte neu laden oder das Original separat öffnen.`);
            const blob=await response.blob();if(request!==version||!dialog.open)return;
            const count=Number(response.headers.get('X-Page-Count'));if(Number.isInteger(count)&&count>=number)pageCount=count;
            if(imageUrl)URL.revokeObjectURL(imageUrl);imageUrl=URL.createObjectURL(blob);
            image.onload=()=>{if(request===version&&dialog.open){image.hidden=false;if(!current)status.textContent='';refreshMark();refreshHints();refreshChecks();hintLayer?.firstElementChild?.scrollIntoView?.({block:'center'});}};
            image.onerror=()=>{if(request===version&&dialog.open)status.textContent='Seitenbild konnte nicht angezeigt werden. Bitte neu laden oder das Original separat öffnen.';};
            image.alt=`PDF-Seite ${number}`;image.src=imageUrl;
        }catch(error){if(request===version&&!abort.signal.aborted)status.textContent=error.message;}
        finally{if(request===version){loading=false;controls();buildSidebar();markSelected();}}
    }
    dialog.showPdfPage=number=>(!loading&&Number.isInteger(number)&&number>=1&&number!==selectedPage&&(!pageCount||number<=pageCount))?loadPage(number):undefined;
    previous.onclick=()=>loadPage(selectedPage-1);next.onclick=()=>loadPage(selectedPage+1);retry.onclick=()=>loadPage(selectedPage);
    container.querySelector('[data-zoom]').onclick=event=>{const zoomed=(wrap||image).classList.toggle('zoomed');event.target.textContent=zoomed?'Einpassen':'Vergrößern';};
    await loadPage(selectedPage);
}
document.addEventListener('click',event=>{const button=event.target.closest('[data-finding-evidence]');if(button)openFindingEvidence(button);});
