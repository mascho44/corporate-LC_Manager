/* Posteingang: Dokumententypen, Zuordnung, Erkennung, Aufteilen von Sammel-PDFs; Hilfsfunktionen (tx, auditLabel, sectionKey). Teil der früheren app.js; die Teile teilen den globalen Gültigkeitsbereich und werden in dieser Reihenfolge geladen (siehe index.html). */
const inboxDocumentTypes=[['COMMERCIAL_INVOICE','Handelsrechnung'],['PACKING_LIST','Packliste'],['BILL_OF_LADING','Konnossement'],['AIR_WAYBILL','Luftfrachtbrief'],['ROAD_CONSIGNMENT_NOTE','CMR'],['CERTIFICATE_OF_ORIGIN','Ursprungszeugnis'],['INSURANCE_CERTIFICATE','Versicherungszertifikat'],['INSPECTION_CERTIFICATE','Inspektionszertifikat'],['BILL_OF_EXCHANGE','Wechsel'],['BENEFICIARY_CERTIFICATE',tx('al.0','Begünstigtenzertifikat')],['QUALITY_CERTIFICATE',tx('al.1','Qualitätszertifikat')],['COURIER_RECEIPT','Kurierbeleg'],['ADVISING_LETTER','Avisierungsschreiben'],['SWIFT_MT700',tx('al.2','SWIFT MT700 / Akkreditiveröffnung')],['ANNEX','Anlage'],['SEA_WAYBILL','Sea Waybill'],['CHARTER_PARTY_BILL_OF_LADING','Charter Party Bill of Lading'],['MULTIMODAL_TRANSPORT_DOCUMENT','Multimodal Transport Document'],['WEIGHT_LIST','Weight List'],['POST_RECEIPT','Post Receipt'],['DOCUMENT_SUBMISSION','Dokumenteneinreichung'],['OTHER','Sonstiges']];
function amendmentDiffHtml(amendment){
    if(!amendment.beforeState||!amendment.afterState)return '<p>'+tx('ah.707b3c','Altbestand: Für diese Änderung wurde noch kein Vorher-/Nachher-Zustand gespeichert.')+'</p>';
    try{
        const before=JSON.parse(amendment.beforeState),after=JSON.parse(amendment.afterState);
const labels={reference:'Aktenreferenz',ownBankReference:'Referenz eigene Bank',foreignBankReference:'Referenz Fremdbank',applicant:'Antragsteller',beneficiary:tx('al.3','Begünstigter'),issuingBank:tx('am.issuingBank','Eröffnende Bank'),advisingBank:'Avisierende Bank',amount:'Betrag',currency:tx('al.4','Währung'),issueDate:'Ausstellungsdatum',expiryDate:'Verfallsdatum (31E)',expiryPlace:'Verfallsort',latestShipmentDate:'Letzter Versandtermin (44C)',sequenceOfTotal:'Sequenz (27)',formOfCredit:'Form des Akkreditivs (40A)',availableWith:tx('am.availableWith','Verfügbar bei / durch (41A/41D)'),draweeBank:'Bezogene Bank (42A)',draftsAt:'Trattenlaufzeit (42C)',mixedPaymentDetails:'Mixed-Payment-Details (42M)',deferredPaymentDetails:'Hinausgeschobene Zahlung (42P)',confirmationInstructions:tx('am.confirmInstr','Bestätigungsanweisung (49)'),reimbursingBank:'Erstattungsbank (53A/53D)',confirmationParty:tx('am.confirmBank','Bank mit Bestätigungswunsch (58A/58D)'),charges:tx('am.charges','Gebühren (71D)'),bankInstructions:tx('am.bankInstr','Anweisungen an die Bank (78)'),requiredDocuments:'Dokumentenanforderungen (46B)',conditions:'Fachliche LC-Bedingungen',additionalFields:'Waren / Bedingungen / Zusatzfelder',rawMessage:tx('am.originalSwift','Ursprüngliche SWIFT-Nachricht')};
        const value=v=>v==null?'–':Array.isArray(v)?v.join('\n'):typeof v==='object'?JSON.stringify(v,null,2):String(v);
        const rows=Object.keys(labels).filter(key=>JSON.stringify(before[key])!==JSON.stringify(after[key]));
        return `<details class="amendment-comparison" open><summary>${tx('ai.b63057','LC-Fassung vor / nach MT707 ·')} ${tx('ax.areas','{n} geänderte Bereiche',{n:rows.length})}</summary>${rows.length?`<div style="overflow-x:auto"><table class="diff-table"><thead><tr><th>${tx('ai.144d62','Feld')}</th><th>${tx('ai.0ce31e','LC vor Amendment')}</th><th>${tx('ai.a941eb','LC nach Amendment')}</th></tr></thead><tbody>${rows.map(key=>`<tr><th>${esc(labels[key])}</th>${diffCells(value(before[key]),value(after[key]))}</tr>`).join('')}</tbody></table></div>`:'<p>'+tx('ah.32e839','Keine Änderung an den unterstützten LC-Feldern. Bitte die ursprüngliche Änderungsanweisung unten fachlich prüfen.')+'</p>'}<p class="diff-legend">${tx('ah.ab6e43','Rot / durchgestrichen: entfernt · Grün / unterstrichen: ergänzt. Verglichen werden die gespeicherten LC-Fassungen, nicht eine automatische Auslegung der MT707-Anweisung.')}</p><details><summary>${tx('ah.b4f9db','Ursprüngliche SWIFT-Nachrichten gegenüberstellen')}</summary><div class="diff-originals"><section><h4>${tx('ah.83bf61','LC / MT700 vor Änderung')}</h4><pre>${esc(before.rawMessage||tx('am.noOriginal','Keine ursprüngliche Nachricht gespeichert.'))}</pre></section><section><h4>${tx('ah.14fa58','Änderungsanweisung MT707')}</h4><pre>${esc(amendment.rawMessage||tx('am.noOriginal','Keine ursprüngliche Nachricht gespeichert.'))}</pre></section></div></details></details>`;
    }catch(error){return '<p>'+tx('ah.0c3979','Gespeicherter Änderungsvergleich konnte nicht angezeigt werden.')+'</p>'}
}
/** Prefill of the assignment form: the only suggested LC file and a recognised or confirmed document type. Nothing is saved before "Zuordnen". */
function inboxPrefill(item){
    const candidates=item?.assignmentCandidates||[],c=item?.classification;
    return {lcId:candidates.length===1?candidates[0].lcId:'',documentType:c&&c.suggestedType&&['SUGGESTED','CONFIRMED'].includes(c.status)?c.suggestedType:''};
}
function renderInboxSuggestions(item){
    const candidates=item.assignmentCandidates||[];if(!candidates.length)return '';
    const reasons={REFERENCE_EXACT:tx('inbox.r.exact','Erkannte LC-Referenz stimmt überein'),REFERENCE_NORMALIZED:tx('inbox.r.normalized','Erkannte Referenz hat eine abweichende Schreibweise – bitte prüfen'),REFERENCE_IN_TEXT:tx('inbox.r.inText','Referenz im Dokumenttext gefunden'),REFERENCE_FORMATTED_IN_TEXT:tx('inbox.r.formatted','Referenz im Text mit abweichenden Trennzeichen – bitte prüfen'),SIBLING_SOURCE:tx('inbox.r.sibling','Ein anderer Teil derselben Quelldatei gehört zu dieser Akte – bitte prüfen')};
    return `<div class="inbox-suggestions"><b>${candidates.length===1?tx('inbox.suggested','Vorgeschlagene LC-Akte'):tx('inbox.suggestedMany','Mehrere mögliche LC-Akten – bitte fachlich prüfen')}</b>${candidates.map(candidate=>`<div class="inbox-suggestion"><div><strong>${esc(candidate.reference)}</strong> <span class="badge">${esc(statusLabel(candidate.status))}</span><small>${esc(reasons[candidate.reason]||candidate.reason)}</small><small class="inbox-suggestion-evidence">${tx('inbox.evidence','Fundstelle')}: ${esc(candidate.evidence)}</small></div><button type="button" class="secondary" data-inbox-candidate="${candidate.lcId}" data-inbox-item="${item.id}">${tx('inbox.chooseSuggestion','Vorschlag auswählen')}</button></div>`).join('')}<small>${tx('inbox.suggestNote','Die Auswahl speichert noch keine Zuordnung. Dokumenttyp prüfen und anschließend bestätigen.')}</small></div>`;
}
function renderClassificationHint(classification,id){
    if(!classification)return '';
    const type=inboxDocumentTypes.find(([value])=>value===classification.suggestedType)?.[1],typeText=type?tx('inbox.type.'+classification.suggestedType,type):null;
    return `<div class="inbox-suggestions"><b>${tx('inbox.typeRecognition','Typerkennung')}: ${esc(typeText||tx('inbox.unclear','Unklar'))}</b><small>${tx('inbox.score','Bewertung {n} / 100 · regelbasiert, keine statistische Wahrscheinlichkeit',{n:Math.round(classification.score*100)})}</small><small>${esc((classification.evidence||[]).join(' · '))}</small>${type?`<button type="button" class="secondary" data-classification-type="${classification.suggestedType}" data-classification-item="${id}">${tx('inbox.chooseType','Typvorschlag auswählen')}</button>`:`<small>${tx('inbox.chooseManual','Dokumenttyp bitte manuell auswählen.')}</small>`}</div>`;
}
document.addEventListener('click',async event=>{
    const selection=event.target.closest('[data-classification-type]');
    if(selection){const form=document.querySelector(`[data-inbox-attach="${selection.dataset.classificationItem}"]`);if(form)form.elements.documentType.value=selection.dataset.classificationType;return;}
    const button=event.target.closest('[data-classification-history]');if(!button)return;
    const dialog=document.createElement('dialog');dialog.className='wide-dialog';dialog.innerHTML='<p>'+tx('ah.efbc6c','Typerkennung wird geladen …')+'</p>';document.body.append(dialog);dialog.showModal();dialog.onclose=()=>dialog.remove();
    try{const history=await json(`/api/documents/${button.dataset.classificationHistory}/classification`);dialog.innerHTML=`<h2>${tx('ai.74caed','Typerkennung & Korrekturen')}</h2>${history.length?history.map(entry=>`<article class="card"><b>${esc(entry.action==='MANUAL'?'Manuelle Auswahl':'Automatischer Vorschlag')} · ${esc(entry.selectedType||entry.suggestedType||'Unklar')}</b><small>${esc(entry.actor||entry.method||'')} · ${esc(entry.at)}</small>${entry.score!=null?`<p>${tx('ai.697fbc','Regelbasierte Bewertung:')} ${Math.round(entry.score*100)} / 100</p>`:''}<p>${esc((entry.evidence||[]).join(' · '))}</p></article>`).join(''):'<p>'+tx('ah.2314d6','Altbestand: keine Klassifikationshistorie gespeichert.')+'</p>'}<p>${tx('ah.b72e3f','Korrektur über „Bearbeiten“ in der Dokumentenakte.')}</p><button type="button">${tx('ah.8311b9','Schließen')}</button>`;}catch(error){dialog.innerHTML=`<p class="error">${esc(error.message)}</p><button type="button">${tx('ah.8311b9','Schließen')}</button>`;}dialog.querySelector('button').onclick=()=>dialog.close();
});
function setupInboxUi(){
    auditLabels.DOCUMENT_INBOX_AUTO_SPLIT=inboxAutoText('audit','Combined PDF split automatically');auditLabels.DOCUMENT_INBOX_AUTO_SPLIT_PART=inboxAutoText('auditPart','Document created by automatic split');auditLabels.DOCUMENT_INBOX_SPLIT='Sammel-PDF aufgeteilt';auditLabels.DOCUMENT_INBOX_SPLIT_PART=tx('al.5','Teil-Dokument aus Sammel-PDF erstellt');
    const section=document.createElement('section');section.id='inboxSection';section.className='panel hidden';
    section.innerHTML='<div class="panelhead"><div><h2 data-i18n="inbox.title">'+tx('ah.105f4a','Dokumentenposteingang')+'</h2><p data-i18n="inbox.intro">'+tx('ah.7b8d85','Dokument ansehen, LC-Akte und Dokumenttyp auswählen, anschließend die Zuordnung bestätigen.')+'</p></div><button id="refreshInbox" type="button" class="secondary" data-i18n="inbox.refresh">'+tx('ai.bf3cfc','Aktualisieren')+'</button></div><form id="inboxUploadForm" class="inbox-upload"><label data-i18n="inbox.pick">'+tx('ah.d55193','Dateien oder ZIP-Archive auswählen')+'<input name="file" type="file" multiple accept=".pdf,.xml,.txt,.csv,.jpg,.jpeg,.png,.zip,application/zip" required></label><small data-i18n="inbox.limits">'+tx('ah.43a0f2','Bis zu 100 entpackte Dateien · maximal 10 MB je Dokument und 50 MB insgesamt. ZIP-Archive bis 50 MB.')+'</small><button type="submit" data-i18n="inbox.upload">'+tx('ah.065c54','In den Posteingang laden')+'</button></form><div id="inboxMessage" class="inbox-message" role="status" aria-live="polite"></div><div id="inboxList"></div>';
    $('#monitoringSection').before(section);
    auditLabels.DOCUMENT_INBOX_UPLOADED=tx('al.6','Dateien im Posteingang aufgenommen');auditLabels.DOCUMENT_INBOX_ATTACHED='Posteingang einer LC-Akte zugeordnet';auditLabels.DOCUMENT_INBOX_DELETED=tx('al.7','Datei aus Posteingang gelöscht');auditLabels.DOCUMENT_INBOX_EXTRACTED='Dokumentenerkennung abgeschlossen';auditLabels.DOCUMENT_INBOX_EXTRACTION_RETRIED='Dokumentenerkennung erneut gestartet';
    $('#refreshInbox').onclick=()=>loadInbox();
    $('#inboxUploadForm').onsubmit=async event=>{
        event.preventDefault();const form=event.target,files=[...form.elements.file.files],button=form.querySelector('button');
        if(files.length>100||files.some(file=>file.size>(file.name.toLowerCase().endsWith('.zip')?50:10)*1024*1024)||files.reduce((sum,file)=>sum+file.size,0)>50*1024*1024){$('#inboxMessage').textContent=tx('inbox.limitsHint','Bitte die Mengen- und Größenlimits beachten.');return;}
        button.disabled=true;$('#inboxMessage').textContent=tx('inbox.uploading','Dateien werden geladen, ZIP-Archive entpackt und Dokumente erkannt …');
        try{const body=new FormData();files.forEach(file=>body.append('file',file));const received=await json('/api/inbox',{method:'POST',body});form.reset();$('#inboxMessage').textContent=tx('inbox.saved','{n} Dateien gespeichert. Die Erkennung läuft im Hintergrund.',{n:received.length});await loadInbox();}
        catch(error){$('#inboxMessage').textContent=tx('inbox.uploadFailed','Upload fehlgeschlagen')+': '+error.message;}
        finally{button.disabled=false;}
    };
}
function renderInboxItem(item,inboxLcs){
return `<article class="inbox-item" data-inbox-id="${item.id}" data-suggested-type="${esc(item.classification?.suggestedType||'')}"><div class="inbox-item-head"><div>${can('DOCUMENT_DELETE')||can('DOCUMENT_UPLOAD')?`<label class="inbox-selection"><input type="checkbox" data-inbox-select="${item.id}" aria-label="${tx('inbox.selectFile','Datei auswählen')}: ${esc(item.originalFilename)}"> ${tx('inbox.select','Auswählen')}</label>`:''}<b>${esc(item.originalFilename)}</b><small>${fileSize(item.fileSize)} · ${tx('inbox.receivedBy','Eingang von')} ${esc(item.receivedBy)} · ${new Intl.DateTimeFormat(globalThis.LcI18n?.locale()||'en-GB',{dateStyle:'short',timeStyle:'short'}).format(new Date(item.receivedAt))}</small><small>${esc(extractionLabel(item.extractionStatus))}${item.extractedDocumentNumber?' · '+tx('inbox.docNo','Dokumentnummer')+' '+esc(item.extractedDocumentNumber):''}</small><small>${item.extractedReference?tx('inbox.refFound','Erkannte LC-Referenz')+': '+esc(item.extractedReference):tx('inbox.refNone','Keine explizite LC-Referenz erkannt.')}</small></div><a class="secondary button-link" target="_blank" rel="noopener" href="/api/inbox/${item.id}/content">${tx('inbox.view','Dokument ansehen')}</a></div>${item.copyHint?.evidence?`<p class="muted">${esc(item.copyHint.evidence)}</p>`:''}${globalThis.LcCopyHint&&globalThis.LcCopyHint.note(item)?`<p class="muted">${esc(globalThis.LcCopyHint.note(item))}</p>`:''}${renderInboxSuggestions(item)}${renderClassificationHint(item.classification,item.id)}<div class="inbox-assignment-workspace">${inboxAssignmentPreview(item)}<div><form data-inbox-attach="${item.id}" class="inbox-assignment"><label>${tx('inbox.lcFile','LC-Akte')}<select name="lcId" required><option value="">${tx('inbox.choose','Bitte auswählen')}</option>${inboxLcs.map(lc=>`<option value="${lc.id}"${inboxPrefill(item).lcId===lc.id?' selected':''}>${esc(lc.reference)} · ${esc(statusLabel(lc.status))}</option>`).join('')}</select></label><label>${tx('inbox.docType','Dokumenttyp')}<select name="documentType" required><option value="">${tx('inbox.choose','Bitte auswählen')}</option>${inboxDocumentTypes.map(([value,label])=>`<option value="${value}"${inboxPrefill(item).documentType===value?' selected':''}>${tx('inbox.type.'+value,label)}</option>`).join('')}</select></label><label>${tx('inbox.copyMark','Kennzeichnung')}<select name="copyNumber">${documentCopyOptions(globalThis.LcCopyHint?globalThis.LcCopyHint.defaultFor(item):item.copyNumber)}</select></label><label>${tx('ds.documentDate','Dokumentdatum')}<input name="documentDate" type="date" value="${esc(item.extractedDocumentDate||item.manualDocumentDate||'')}"><small data-field-quality="documentDate">${item.extractedDocumentDate?tx('inbox.dateAuto','Automatisch erkannt – bitte prüfen'):item.manualDocumentDate?tx('inbox.dateSplit','Beim Aufteilen eingetragen'):['REVIEW','AMBIGUOUS'].includes(item.documentDateRecognitionStatus)?tx('inbox.dateAmbiguous','Datumsangabe nicht eindeutig – bitte manuell prüfen'):tx('inbox.dateNone','Kein eindeutiges Dokumentdatum erkannt')}</small></label>${item.signatureConfirmed?`<p class="inbox-signature-note">✓ ${tx('inbox.signature','Unterschrift beim Aufteilen bestätigt – wird mit der Zuordnung als Prüfdatum übernommen.')}</p>`:''}${inboxMetadataFields(item)}<button type="submit">${tx('inbox.confirm','Zuordnung bestätigen')}</button>${can('DOCUMENT_DELETE')?`<button type="button" class="danger" data-inbox-delete="${item.id}">${tx('ak.delete','Löschen')}</button>`:''}</form>${inboxNewCaseHtml(item)}</div></div></article>`;
}
function inboxAssignmentPreview(item){
    const url=`/api/inbox/${encodeURIComponent(item.id)}/content`,label=esc(item.originalFilename||tx('inbox.document','Dokument'));
    const pages=`/api/inbox/${encodeURIComponent(item.id)}/pages/1/preview?enlarged=true`;
    const content=item.contentType==='application/pdf'?`<div class="inbox-pdf-pages" data-pdf-page="1"><div class="inbox-pdf-nav"><button type="button" class="secondary" data-pdf-prev disabled>${tx('inbox.prevPage','Vorherige Seite')}</button><span data-pdf-label>${tx('inbox.page','Seite {n}',{n:1})}</span><button type="button" class="secondary" data-pdf-next>${tx('inbox.nextPage','Nächste Seite')}</button></div><img data-preview-src="${pages}" alt="${tx('inbox.pdfPage','PDF-Seite')}: ${label}"></div>`:
        ['image/png','image/jpeg'].includes(item.contentType)?`<img data-preview-src="${url}" alt="${tx('inbox.preview','Dokumentvorschau')}: ${label}">`:`<p>${tx('inbox.openOriginalFormat','Für dieses Format bitte das Original separat öffnen.')}</p>`;
    return `<details class="inbox-assignment-preview"><summary>${tx('inbox.previewFor','Vorschau zur Zuordnung')}</summary><div>${content}<p>${tx('inbox.blocked','Falls die Vorschau blockiert wird:')} <a target="_blank" rel="noopener" href="${url}">${tx('inbox.openOriginal','Original separat öffnen')}</a></p></div></details>`;
}
function documentCopyLabel(value){return value==null?tx('inbox.unmarked','Nicht gekennzeichnet'):Number(value)===0?'Original':Number(value)<0?`Original ${-Number(value)}`:Number(value)===1?'Copy':`Copy ${Number(value)}`;}
function documentCopyOptions(value){const options=[['',tx('inbox.unmarked','Nicht gekennzeichnet')],['0','Original'],['1','Copy']];if(value!=null&&value!==''&&![0,1].includes(Number(value)))options.push([String(value),documentCopyLabel(value)+' '+tx('inbox.formerly','(bisher)')]);return options.map(([key,label])=>`<option value="${key}"${String(value??'')===key?' selected':''}>${label}</option>`).join('');}
function readDocumentCopy(value){return value===''||value==null?null:Number(value);}
let inboxRefreshTimer=null;
function tx(key,de,values={}){const s=globalThis.LcI18n?.t?globalThis.LcI18n.t(key,values):null;return s&&s!==key?s:String(de).replace(/\{(\w+)\}/g,(_,n)=>String(values[n]??`{${n}}`));}
function findingMessage(r){return globalThis.LcI18n?.language?.()==='en'&&r.messageEn?r.messageEn:r.message;}
/** Which dossier part a block is: the key on its heading, or (older markup) its German title. */
function sectionKey(node){const h=node?.querySelector?.('h3');return h?.dataset?.sect||({'Dokumentenakte':'documents','Automatische Prüfung':'checks','Erforderliche Dokumente laut LC':'required','Amendment-Historie':'amendments'})[h?.textContent]||null;}
function auditLabel(action){return tx('audit.act.'+action,auditLabels[action]||action);}
function inboxAutoText(key,fallback){const value=globalThis.LcI18n?.t('inbox.autoSplit.'+key);return value&&value!=='inbox.autoSplit.'+key?value:fallback;}
let inboxLoadVersion=0,inboxBulkDeleting=false;
const inboxEditedItems=new Set();
function inboxBulkToolbar(){if(!can('DOCUMENT_DELETE')&&!can('DOCUMENT_UPLOAD'))return '';return '<div class="document-bulk-actions"><label><input type="checkbox" data-inbox-select-all> '+tx('inbox.selectAll','Alle angezeigten auswählen')+'</label>'+(can('DOCUMENT_DELETE')?'<button type="button" class="danger" data-inbox-delete-selected disabled>'+tx('inbox.deleteSelected','Ausgewählte löschen')+'</button>':'')+(can('DOCUMENT_UPLOAD')?'<button type="button" class="secondary" data-inbox-attach-selected>'+tx('inbox.attachSelected','Ausgewählte zuordnen')+'</button><button type="button" data-inbox-attach-ready title="'+esc(tx('inbox.attachReadyTitle','Alle Dateien, bei denen LC-Akte und Dokumenttyp gefüllt sind, jeweils ihrer Akte zuordnen'))+'">'+tx('inbox.attachReady','Eindeutige zuordnen')+'</button>':'')+'</div>';}
/** Inbox forms that are ready: LC file and document type filled and the metadata valid. */
function inboxReadyForms(list){return [...list.querySelectorAll('[data-inbox-attach]')].filter(form=>form.elements.lcId.value&&form.elements.documentType.value&&!form.elements.documentType.disabled&&form.checkValidity());}
document.addEventListener('click',async event=>{
 const ready=event.target.closest('[data-inbox-attach-ready]');
 if(ready){
  if(inboxBulkDeleting||!can('DOCUMENT_UPLOAD'))return;
  const list=$('#inboxList'),forms=inboxReadyForms(list);
  if(!forms.length){$('#inboxMessage').textContent=tx('inbox.noReady','Keine Datei mit eindeutig gefüllter LC-Akte und Dokumenttyp.');return;}
  const rows=forms.map(form=>{const id=form.dataset.inboxAttach,name=list.querySelector(`[data-inbox-id="${id}"] .inbox-item-head b`)?.textContent||id;return {id,form,name,lc:form.elements.lcId.selectedOptions[0]?.textContent||'',type:form.elements.documentType.selectedOptions[0]?.textContent||''};});
  const dialog=document.createElement('dialog');dialog.className='wide-dialog inbox-ready-dialog';
  dialog.innerHTML=`<div class="dialoghead"><h2>${tx('ah.e9c01c','Eindeutige Dokumente zuordnen')}</h2><button type="button" data-close>${tx('ah.8311b9','Schließen')}</button></div><p>${rows.length===1?tx('b4.pu1','1 Datei mit gefüllter LC-Akte und Dokumenttyp. Bitte prüfen; jede Datei geht in ihre eigene Akte.'):tx('b4.pu2','{n} Dateien mit gefüllter LC-Akte und Dokumenttyp. Bitte prüfen; jede Datei geht in ihre eigene Akte.',{n:rows.length})}</p><table class="pu-table"><tr><th>${tx('ah.e2aa67','Datei')}</th><th>${tx('ai.c38ff7','LC-Akte')}</th><th>${tx('ah.950bb1','Dokumenttyp')}</th></tr>${rows.map(r=>`<tr><td>${esc(r.name)}</td><td>${esc(r.lc)}</td><td>${esc(r.type)}</td></tr>`).join('')}</table><p data-ready-status role="status"></p><div class="actions"><button type="button" data-ready-confirm>${rows.length} zuordnen</button></div>`;
  document.body.append(dialog);dialog.querySelector('[data-close]').onclick=()=>dialog.close();dialog.addEventListener('close',()=>dialog.remove());dialog.showModal();
  dialog.querySelector('[data-ready-confirm]').onclick=async event=>{
   const button=event.currentTarget;button.disabled=true;const status=dialog.querySelector('[data-ready-status]');let done=0;const failures=[];
   for(const r of rows){try{await json(`/api/inbox/${r.id}/attach`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(inboxAttachment(r.form,r.form.elements.lcId.value))});done++;inboxEditedItems.delete(r.id);}catch(error){failures.push(r.name+': '+error.message);}status.textContent=tx('at.8f86f6','{p0} von {p1} zugeordnet',{p0:done,p1:rows.length});}
   $('#inboxMessage').textContent=tx('at.528781','{p0} von {p1} Dateien zugeordnet',{p0:done,p1:rows.length})+(failures.length?' · Fehler: '+failures.join(' · '):'');
   dialog.close();await loadInbox();
  };
  return;
 }
 const evidence=event.target.closest('[data-inbox-evidence]');if(evidence){evidence.disabled=true;const target=evidence.parentElement.querySelector('[data-metadata-evidence]');try{const facts=await json(`/api/inbox/${evidence.dataset.inboxEvidence}/metadata-evidence`);target.innerHTML='<p>'+tx('ah.f81b65','Textfundstellen aus der Erkennung; keine verifizierte Seitenposition. Bitte am Original prüfen.')+'</p>'+Object.entries(facts).map(([key,value])=>`<p><b>${esc(key)}</b></p><pre>${esc(value)}</pre>`).join('');if(!Object.keys(facts).length)target.textContent='Keine eindeutige Textfundstelle verfügbar.';}catch(error){target.textContent=error.message;}finally{evidence.disabled=false;}return;}
 const button=event.target.closest('[data-inbox-attach-selected]');if(!button||inboxBulkDeleting||!can('DOCUMENT_UPLOAD'))return;
 const list=$('#inboxList'),ids=[...list.querySelectorAll('[data-inbox-select]:checked')].map(box=>box.dataset.inboxSelect);
 if(!ids.length){$('#inboxMessage').textContent=tx('inbox.pickFiles','Bitte Dateien auswählen.');return;}
 const forms=ids.map(id=>list.querySelector(`[data-inbox-attach="${id}"]`));
 if(forms.some(form=>!form||form.elements.documentType.disabled)){$('#inboxMessage').textContent=tx('inbox.waitRecognition','Bitte die laufende Erkennung der ausgewählten Dateien abwarten.');return;}
 const dialog=document.createElement('dialog');dialog.className='wide-dialog';dialog.innerHTML=`<form><div class="dialoghead"><h2>${tx('ah.7ed673','Dokumente gemeinsam zuordnen')}</h2><button type="button" data-bulk-cancel>${tx('ah.8311b9','Schließen')}</button></div><p>${tx('ah.21669d','Eine Zielakte für alle Dateien wählen. Dokumenttypen und Metadaten am Original prüfen.')}</p><div class="form-grid"><label class="wide">${tx('ai.c38ff7','LC-Akte')}<select name="lcId" required>${forms[0].elements.lcId.innerHTML}</select></label>${forms.map((form,i)=>`<label class="wide">${esc(list.querySelector(`[data-inbox-id="${ids[i]}"] b`).textContent)}<select name="type${i}" required>${form.elements.documentType.innerHTML}</select></label>`).join('')}</div><div class="actions"><button type="submit">${tx('ah.4f654e','Zuordnung bestätigen')}</button></div><p role="alert" data-bulk-error></p></form>`;
 document.body.append(dialog);dialog.querySelector('[data-bulk-cancel]').onclick=()=>dialog.close();dialog.addEventListener('close',()=>dialog.remove());dialog.showModal();
 const form=dialog.querySelector('form');forms.forEach((source,i)=>form.elements['type'+i].value=source.elements.documentType.value);
 form.onsubmit=async event=>{event.preventDefault();if(!form.reportValidity())return;
 const lcId=form.elements.lcId.value;forms.forEach((source,i)=>{source.elements.lcId.value=lcId;source.elements.documentType.value=form.elements['type'+i].value;});
 if(forms.some(source=>!source.reportValidity())){dialog.querySelector('[data-bulk-error]').textContent=tx('inbox.fixMetadata','Bitte ungültige Metadaten in den Dokumenten korrigieren.');return;}
 if(!await confirmAction(tx('at.c9db02','{p0} Dokumente der ausgewählten LC-Akte zuordnen?',{p0:ids.length})))return;
 const payloads=forms.map(source=>inboxAttachment(source,lcId)),controls=[...list.querySelectorAll('button,input,select')].map(control=>[control,control.disabled]);
 inboxBulkDeleting=true;++inboxLoadVersion;clearTimeout(inboxRefreshTimer);dialog.querySelectorAll('button,select').forEach(control=>control.disabled=true);controls.forEach(([control])=>control.disabled=true);
 let attached=0;const failures=[];
 try{for(let i=0;i<ids.length;i++){try{await json(`/api/inbox/${ids[i]}/attach`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payloads[i])});attached++;inboxEditedItems.delete(ids[i]);list.querySelector(`[data-inbox-id="${ids[i]}"]`)?.remove();}catch(error){failures.push(error.message);}$('#inboxMessage').textContent=tx('at.a6e14f','{p0} von {p1} zugeordnet',{p0:attached,p1:ids.length});}}
 finally{inboxBulkDeleting=false;controls.forEach(([control,disabled])=>control.disabled=disabled);dialog.close();updateInboxSelection(list);}
 await loadInbox(true);$('#inboxMessage').textContent=failures.length?tx('at.2d7c19','{p0} zugeordnet, {p1} nicht zugeordnet: {p2}',{p0:attached,p1:failures.length,p2:failures[0]}):`${attached} Dokumente zugeordnet.`;
 };
});
function inboxMetadataFields(item){return `<details><summary>${tx('md.title','Metadaten prüfen und korrigieren')}</summary><label>${tx('md.ref','LC-Referenz im Dokument')}<input name="metadataReference" maxlength="255" value="${esc(item.extractedReference||'')}"><small data-field-quality="metadataReference">${item.extractedReference?tx('md.found','Erkannt – prüfen'):tx('md.unsure','Nicht erkannt / unsicher')}</small></label><label>${tx('inbox.docNo','Dokumentnummer')}<input name="metadataNumber" maxlength="255" value="${esc(item.extractedDocumentNumber||'')}"><small data-field-quality="metadataNumber">${item.extractedDocumentNumber?tx('md.found','Erkannt – prüfen'):tx('md.unsure','Nicht erkannt / unsicher')}</small></label><label>${tx('de.amount','Betrag')}<input name="metadataAmount" type="number" min="0" step="0.01" value="${item.extractedAmount??''}"><small data-field-quality="metadataAmount">${item.extractedAmount!=null?tx('md.found','Erkannt – prüfen'):tx('md.unsure','Nicht erkannt / unsicher')}</small></label><label>${tx('de.currency','Währung')}<input name="metadataCurrency" maxlength="3" pattern="[A-Z]{3}" value="${esc(item.extractedCurrency||'')}"><small data-field-quality="metadataCurrency">${item.extractedCurrency?tx('md.found','Erkannt – prüfen'):tx('md.unsure','Nicht erkannt / unsicher')}</small></label><label>${tx('md.profile','Firmen-/Banklayoutprofil')}<input name="spatialProfile" maxlength="100" placeholder="${tx('md.profileHint','Zum Beispiel Exporteur – Rechnungen')}"></label><button type="button" class="secondary" data-metadata-save>${tx('md.save','Bearbeitungsstand speichern')}</button><button type="button" class="secondary" data-spatial-confirm>${tx('md.layout','Layout bestätigen & lernen')}</button><button type="button" class="secondary" data-metadata-positions>${tx('md.positions','Positionsbasierte Vorschläge')}</button><button type="button" class="secondary" data-metadata-suggest>${tx('md.learned','Gelernte Vorschläge prüfen')}</button><button type="button" class="secondary" data-metadata-confirm>${tx('md.confirm','Metadaten bestätigen & lernen')}</button><p data-metadata-training-status role="status"></p><button type="button" class="secondary" data-inbox-evidence="${item.id}">${tx('md.evidence','Fundstellen anzeigen')}</button><div data-metadata-evidence></div><small>${tx('md.note','Korrekturen werden bei der Zuordnung übernommen. Die LC-Stammdaten ändern sich nicht.')}</small></details>`;}
function inboxAttachment(form,lcId){return {lcId,documentType:form.elements.documentType.value,documentDate:form.elements.documentDate.value||null,copyNumber:readDocumentCopy(form.elements.copyNumber.value),metadata:{reference:form.elements.metadataReference.value.trim()||null,documentNumber:form.elements.metadataNumber.value.trim()||null,amount:form.elements.metadataAmount.value===''?null:Number(form.elements.metadataAmount.value),currency:form.elements.metadataCurrency.value.trim()||null}};}
async function openDocumentDuplicates(url){
 try{
 const report=await json(url);
 let dialog=document.getElementById('documentDuplicatesDialog');if(!dialog){dialog=document.createElement('dialog');dialog.id='documentDuplicatesDialog';dialog.className='wide-dialog';document.body.append(dialog);}if(dialog.open)dialog.close();
 dialog.innerHTML='<h2>'+tx('ah.023543','Identische Dateien prüfen')+'</h2><p>'+report.checked+tx('al.10',' Dateien geprüft')+(report.limited?tx('al.11',' · begrenzte Prüfung (maximal 100 Dateien / 100 MB)'):'')+'. Verglichen wird der Dateiinhalt, nicht die Echtheit eines Originals. Originale und Kopien bleiben getrennt; es wird nichts automatisch gelöscht.</p>'+report.groups.map(group=>'<article class="card"><b>'+tx('ai.b68f56','Identischer Dateiinhalt')+'</b><ul>'+group.documents.map(doc=>'<li>'+esc(doc.filename)+' · '+esc(doc.copyNumber==null?'Nicht gekennzeichnet':doc.copyNumber===0?'Original':doc.copyNumber<0?'Original '+Math.abs(doc.copyNumber):'Copy '+doc.copyNumber)+'</li>').join('')+'</ul></article>').join('')+(!report.groups.length?'<p>'+tx('ah.0d7396','Keine identischen Dateien im geprüften Bestand gefunden.')+'</p>':'')+'<button type="button">'+tx('ah.8311b9','Schließen')+'</button>';
 dialog.querySelector('button').onclick=()=>dialog.close();dialog.showModal();
 }catch(error){alert(tx('al.12','Dublettenprüfung fehlgeschlagen: ')+error.message);}
}
async function openDocumentLayouts(){
 try{
 const rows=await json('/api/settings/document-layouts');
 let dialog=document.getElementById('documentLayoutsDialog');if(!dialog){dialog=document.createElement('dialog');dialog.id='documentLayoutsDialog';dialog.className='wide-dialog';document.body.append(dialog);}if(dialog.open)dialog.close();
 const active=new Map();rows.filter(row=>row.active).forEach(row=>{if(!active.has(row.profile))active.set(row.profile,new Set());active.get(row.profile).add(row.labels_json);});
 dialog.innerHTML='<div class="dialoghead"><h2>'+tx('ai.d88d87','Gelernte Layoutprofile')+'</h2><button type="button" data-close>'+tx('ah.8311b9','Schließen')+'</button></div><p>'+tx('ah.b42298','Bestätigungen bleiben erhalten. Deaktivierte Muster werden nicht mehr verwendet. Bei widersprüchlichen aktiven Mustern bitte fachlich entscheiden, welche Bestätigung gültig ist.')+'</p>'+rows.map(row=>'<form data-layout-id="'+esc(row.id)+'"><label>'+tx('ai.546425','Profilname')+'<input name="profile" maxlength="100" required value="'+esc(row.profile)+'"></label><label><input type="checkbox" name="active" '+(row.active?'checked':'')+'> '+tx('ai.c44e07','Aktiv')+'</label><p>'+esc(row.confirmed_by)+' · '+esc(String(row.confirmed_at))+'</p>'+(active.get(row.profile)?.size>1?'<p class="error">'+tx('ah.27122c','Widersprüchliche aktive Muster in diesem Profil')+'</p>':'')+'<pre>'+esc(row.labels_json)+'</pre><button type="submit">'+tx('ah.2aecef','Änderungen speichern')+'</button><p data-status role="status"></p></form>').join('');
 dialog.querySelector('[data-close]').onclick=()=>dialog.close();
 dialog.querySelectorAll('[data-layout-id]').forEach(form=>form.onsubmit=async event=>{event.preventDefault();const button=form.querySelector('button');button.disabled=true;try{await json('/api/settings/document-layouts/'+form.dataset.layoutId,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({profile:form.elements.profile.value.trim(),active:form.elements.active.checked})});await openDocumentLayouts();}catch(error){form.querySelector('[data-status]').textContent=error.message;}finally{button.disabled=false;}});
 dialog.showModal();
 }catch(error){$('#inboxMessage').textContent=tx('al.13','Layoutprofile konnten nicht geladen werden: ')+error.message;}
}
function restoreInboxReview(form,item){
 if(!form||!item.metadataReviewJson)return;
 try{const review=JSON.parse(item.metadataReviewJson),m=review.metadata||{};
 const values={lcId:review.lcId,documentType:review.documentType,copyNumber:review.copyNumber,documentDate:review.documentDate,spatialProfile:review.profile,metadataReference:m.reference,metadataNumber:m.documentNumber,metadataAmount:m.amount,metadataCurrency:m.currency};
 Object.entries(values).forEach(([name,value])=>{if(form.elements[name])form.elements[name].value=value??'';});
 form.querySelectorAll('[data-field-quality]').forEach(node=>node.textContent=review.confirmed?tx('al.14','Manuell bestätigt'):tx('al.15','Gespeicherte Korrektur – prüfen'));
 form.querySelector('[data-metadata-training-status]').textContent=review.confirmed?tx('al.16','Gespeicherte Bestätigung wiederhergestellt.'):tx('al.17','Gespeicherter Bearbeitungsstand wiederhergestellt – noch nicht bestätigt.');
 }catch(error){form.querySelector('[data-metadata-training-status]').textContent=tx('al.18','Gespeicherter Bearbeitungsstand konnte nicht geladen werden. Bitte erneut prüfen.');}
}
function inboxReviewPayload(form){
 const payload={...inboxAttachment(form,form.elements.lcId.value||null),profile:form.elements.spatialProfile.value.trim()||null,confirmed:false};
 payload.documentType=payload.documentType||null;return payload;
}
async function saveInboxReview(form){
 const payload=inboxReviewPayload(form);
 await json(`/api/inbox/${form.dataset.inboxAttach}/metadata-review`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
 return payload;
}
document.addEventListener('click',async event=>{
 const button=event.target.closest('[data-metadata-save]');if(!button)return;
 const form=button.closest('[data-inbox-attach]'),status=form.querySelector('[data-metadata-training-status]');
 if(['documentDate','metadataReference','metadataNumber','metadataAmount','metadataCurrency','spatialProfile'].some(name=>!form.elements[name].reportValidity()))return;
 button.disabled=true;
 try{const saved=await saveInboxReview(form);const current=inboxReviewPayload(form);status.textContent=JSON.stringify(saved)===JSON.stringify(current)?tx('al.19','Bearbeitungsstand gespeichert – noch nicht als Training bestätigt.'):tx('al.20','Bearbeitungsstand gespeichert; neuere Eingaben sind noch nicht gespeichert.');}catch(error){status.textContent=tx('imp.saveFailed','Speichern fehlgeschlagen: ')+error.message;}finally{button.disabled=false;}
});
document.addEventListener('input',event=>{
 const form=event.target.closest('[data-inbox-attach]');if(!form||!['documentDate','metadataReference','metadataNumber','metadataAmount','metadataCurrency'].includes(event.target.name))return;
 const quality=form.querySelector(`[data-field-quality="${event.target.name}"]`);if(quality)quality.textContent=tx('al.21','Manuell bearbeitet – noch nicht bestätigt');
 const status=form.querySelector('[data-metadata-training-status]');if(status)status.textContent=tx('al.22','Änderungen noch nicht als Training bestätigt.');
});
document.addEventListener('click',async event=>{
 const button=event.target.closest('[data-metadata-confirm],[data-metadata-suggest]');if(!button)return;
 const form=button.closest('[data-inbox-attach]'),status=form.querySelector('[data-metadata-training-status]'),id=form.dataset.inboxAttach;
 const names=['documentDate','metadataReference','metadataNumber','metadataAmount','metadataCurrency'];
 if(button.hasAttribute('data-metadata-confirm')){
  if(names.some(name=>!form.elements[name].reportValidity()))return;
  if(!await confirmAction(tx('al.23','Alle Metadaten am Original geprüft? Diese Bestätigung wird mandantenspezifisch gespeichert und protokolliert.')))return;
 }
 button.disabled=true;
 try{
  if(button.hasAttribute('data-metadata-confirm')){const values=inboxAttachment(form,null);await saveInboxReview(form);await json(`/api/inbox/${id}/metadata-training`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({metadata:values.metadata,documentDate:values.documentDate})});if(JSON.stringify(values)!==JSON.stringify(inboxAttachment(form,null))){status.textContent=tx('al.24','Bestätigung gespeichert, aber die Eingaben wurden inzwischen geändert. Erneut prüfen.');return;}form.querySelectorAll('[data-field-quality]').forEach(node=>node.textContent=tx('al.14','Manuell bestätigt'));status.textContent=tx('al.25','Bestätigung gespeichert. Lernen ist unabhängig von der LC-Zuordnung.');}
  else{const proposal=await json(`/api/inbox/${id}/metadata-training`);if(!['LEARNED_REVIEW','ANCHOR_REVIEW'].includes(proposal.status)){status.textContent=proposal.status==='CONFLICT'?tx('al.26','Widersprüchliche Bestätigungen – keine automatische Übernahme.'):tx('al.27','Kein eindeutig passendes bestätigtes Textmuster vorhanden.');return;}
   status.textContent=proposal.status==='ANCHOR_REVIEW'?tx('al.28','Gelernte Textanker: neue Werte aus diesem Dokument gelesen. Bitte prüfen.'):tx('al.29','Bestätigtes Muster mit identischem Text gefunden. Bitte prüfen.');
   if(!await confirmAction(tx('al.30','Gelernten Vorschlag in die Bearbeitungsfelder übernehmen? Aktuelle Eingaben werden ersetzt.')))return;
   const m=proposal.values.metadata;Object.entries({documentDate:proposal.values.documentDate,metadataReference:m.reference,metadataNumber:m.documentNumber,metadataAmount:m.amount,metadataCurrency:m.currency}).forEach(([name,value])=>{const key={documentDate:'documentDate',metadataReference:'reference',metadataNumber:'documentNumber',metadataAmount:'amount',metadataCurrency:'currency'}[name];if(proposal.status!=='ANCHOR_REVIEW'||Object.hasOwn(proposal.evidence||{},key))form.elements[name].value=value??'';});form.querySelectorAll('[data-field-quality]').forEach(node=>{const key={documentDate:'documentDate',metadataReference:'reference',metadataNumber:'documentNumber',metadataAmount:'amount',metadataCurrency:'currency'}[node.dataset.fieldQuality];if(proposal.status!=='ANCHOR_REVIEW'||Object.hasOwn(proposal.evidence||{},key))node.textContent='Gelernter Vorschlag – prüfen';});if(proposal.status==='ANCHOR_REVIEW')form.querySelector('[data-metadata-evidence]').innerHTML='<p>'+tx('ah.5ca871','Textanker-Fundstellen aus dem neuen Beleg (keine verifizierten Seitenpositionen):')+'</p>'+Object.entries(proposal.evidence).map(([key,value])=>`<p><b>${esc(key)}</b></p><pre>${esc(value)}</pre>`).join('');inboxEditedItems.add(id);
  }
 }catch(error){status.textContent=error.message;}finally{button.disabled=false;}
});
document.addEventListener('click',async event=>{
 const button=event.target.closest('[data-metadata-positions]');if(!button)return;
 const form=button.closest('[data-inbox-attach]'),id=form.dataset.inboxAttach,status=form.querySelector('[data-metadata-training-status]'),target=form.querySelector('[data-metadata-evidence]');button.disabled=true;
 try{const query='?profile='+encodeURIComponent(form.elements.spatialProfile.value.trim()),fields=await json(`/api/inbox/${id}/metadata-positions${query}`);if(!Object.keys(fields).length){status.textContent=tx('al.31','Keine eindeutige positionsbasierte Zuordnung. Ältere Dateien bei Bedarf erneut erkennen lassen.');return;}
  target.innerHTML='<p>'+tx('ah.5a9dbb','Markierte Fundstellen – am Original prüfen. Koordinaten in 200-DPI-Pixeln.')+'</p>'+Object.entries(fields).map(([key,value])=>`<p><b>${esc(key)}</b>: ${esc(value.value)} · Seite ${value.page}</p><img class="metadata-field-crop" loading="lazy" src="/api/inbox/${id}/metadata-positions/${encodeURIComponent(key)}/preview${esc(query)}" alt="Markierter PDF-Ausschnitt für ${esc(key)}"><small>x ${value.left}, y ${value.top}, ${value.width} × ${value.height}</small><pre>${esc(value.evidence)}</pre><a target="_blank" rel="noopener" href="/api/inbox/${id}/content#page=${value.page}">${tx('ah.00efec','Originalseite öffnen')}</a>`).join('');target.querySelectorAll('img').forEach(image=>image.onerror=()=>{const text=document.createElement('p');text.textContent='Ausschnitt nicht verfügbar. Bitte Originalseite öffnen.';image.replaceWith(text);});
  if(!await confirmAction(tx('al.32','Positionsbasierte Vorschläge übernehmen? Nur gefundene Felder werden ersetzt; bitte fachlich prüfen.')))return;
  const names={reference:'metadataReference',documentNumber:'metadataNumber',documentDate:'documentDate',amount:'metadataAmount',currency:'metadataCurrency'};Object.entries(fields).forEach(([key,value])=>{const name=names[key];if(!name)return;form.elements[name].value=value.value;const quality=form.querySelector(`[data-field-quality="${name}"]`);if(quality)quality.textContent=tx('al.33','Positionsvorschlag – prüfen');});inboxEditedItems.add(id);status.textContent=tx('al.34','Aktuelle Dokumentwerte aus Positionen vorgeschlagen – noch nicht bestätigt.');
 }catch(error){status.textContent=error.message;}finally{button.disabled=false;}
});
document.addEventListener('click',async event=>{
 const button=event.target.closest('[data-spatial-confirm]');if(!button)return;const form=button.closest('[data-inbox-attach]'),status=form.querySelector('[data-metadata-training-status]'),profile=form.elements.spatialProfile.value.trim();
 if(!profile){status.textContent=tx('al.35','Bitte ein Firmen-/Banklayoutprofil benennen.');return;}
 if(['documentDate','metadataReference','metadataNumber','metadataAmount','metadataCurrency','spatialProfile'].some(name=>!form.elements[name].reportValidity()))return;
 if(!await confirmAction(tx('al.36','Metadaten und räumliche Zuordnungen am Original geprüft? Layoutanker für dieses Mandantenprofil speichern?')))return;
 button.disabled=true;const values=inboxAttachment(form,null);
 try{await json(`/api/inbox/${form.dataset.inboxAttach}/spatial-training`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({profile,values:{metadata:values.metadata,documentDate:values.documentDate}})});status.textContent=tx('al.37','Layoutanker gespeichert. Neue Belege mit diesem Profil bleiben prüfpflichtig.');}catch(error){status.textContent=error.message;}finally{button.disabled=false;}
});
function updateInboxSelection(list){
 const boxes=[...list.querySelectorAll('[data-inbox-select]')],count=boxes.filter(box=>box.checked).length,all=list.querySelector('[data-inbox-select-all]'),button=list.querySelector('[data-inbox-delete-selected]');
 if(all){all.checked=boxes.length>0&&count===boxes.length;all.indeterminate=count>0&&count<boxes.length;}
 if(button){button.disabled=!count||inboxBulkDeleting;button.textContent=tx('at.a13d7c','Ausgewählte löschen ({p0})',{p0:count});}
}
document.addEventListener('change',event=>{
 if(!event.target.matches('[data-inbox-select],[data-inbox-select-all]'))return;
 const list=$('#inboxList');if(!list)return;
 if(event.target.matches('[data-inbox-select-all]'))list.querySelectorAll('[data-inbox-select]').forEach(box=>box.checked=event.target.checked);
 updateInboxSelection(list);
});
document.addEventListener('click',async event=>{
 const button=event.target.closest('[data-inbox-delete-selected]');if(!button||button.disabled||inboxBulkDeleting||!can('DOCUMENT_DELETE'))return;
 const list=$('#inboxList'),ids=[...list.querySelectorAll('[data-inbox-select]:checked')].map(box=>box.dataset.inboxSelect);
 if(!ids.length||!await confirmAction(tx('at.e9919d','{p0} ausgewählte Dateien endgültig aus dem Posteingang löschen?',{p0:ids.length})))return;
 inboxBulkDeleting=true;++inboxLoadVersion;clearTimeout(inboxRefreshTimer);
 const controls=[...list.querySelectorAll('button,input,select')].map(control=>[control,control.disabled]);controls.forEach(([control])=>control.disabled=true);
 let deleted=0;const failures=[];
 try{for(const id of ids){try{await json(`/api/inbox/${id}`,{method:'DELETE'});deleted++;inboxEditedItems.delete(id);list.querySelector(`[data-inbox-id="${id}"]`)?.remove();}catch(error){failures.push(error.message);}$('#inboxMessage').textContent=tx('at.bc3f46','{p0} von {p1} Dateien gelöscht',{p0:deleted,p1:ids.length});}}
 finally{inboxBulkDeleting=false;controls.forEach(([control,disabled])=>control.disabled=disabled);updateInboxSelection(list);}
 await loadInbox(true);
 $('#inboxMessage').textContent=failures.length?tx('at.74de99','{p0} gelöscht, {p1} nicht gelöscht: {p2}',{p0:deleted,p1:failures.length,p2:failures[0]}):tx('at.3f1538','{p0} Dateien gelöscht.',{p0:deleted});
});
function inboxPending(item){return ['QUEUED','PROCESSING'].includes(item.extractionStatus);}
function inboxStatusHtml(item){const pages=item.recognitionPages||[],completed=pages.filter(page=>['EXTRACTED','OCR_EXTRACTED'].includes(page.status)).length;const diagnostics=(item.recognitionPages||[]).filter(page=>['FAILED','OCR_TIMEOUT','NO_TEXT'].includes(page.status));const pageErrors=diagnostics.length?'<details><summary>'+tx('ah.20020e','Seiten mit Erkennungsproblemen (')+''+diagnostics.length+')</summary>'+diagnostics.map(page=>'<p>'+tx('ah.4789ac','Seite')+' '+page.page+': '+esc(extractionLabel(page.status))+'</p>').join('')+'</details>':'';const corrections=pages.filter(page=>Number.isFinite(page.correctionDegrees)&&Math.abs(page.correctionDegrees)>.1).map(page=>'<small>'+tx('ah.4789ac','Seite')+' '+page.page+': OCR-Ausrichtung um '+Math.round(page.correctionDegrees*10)/10+'° korrigiert · Original unverändert</small>').join('');return inboxPending(item)?`<div class="inbox-processing" role="status">${esc(extractionLabel(item.extractionStatus))}<progress aria-label="${tx('ah.fd1f06','Dokumentenerkennung läuft')}" ${pages.length?`value="${completed}" max="${pages.length}"`:''}></progress>${pages.length?`<small>${completed} / ${pages.length} Seiten erkannt</small>`:''}<small>${tx('ah.1ca1fd','Die Datei ist gespeichert. Du kannst diese Seite verlassen; die Erkennung läuft weiter.')}</small></div>`:`<small>${esc(extractionLabel(item.extractionStatus))}</small>${corrections}${pageErrors}${can('DOCUMENT_UPLOAD')&&['FAILED','OCR_PARTIAL','OCR_TIMEOUT','OCR_UNAVAILABLE','OCR_PAGE_LIMIT','NO_TEXT','NOT_PROCESSED'].includes(item.extractionStatus)?'<button type="button" class="secondary" data-inbox-retry="'+item.id+'">'+tx('ai.1204b2','Erkennung erneut starten')+'</button>':can('DOCUMENT_UPLOAD')&&['EXTRACTED','OCR_EXTRACTED'].includes(item.extractionStatus)?'<button type="button" class="secondary" data-inbox-retry="'+item.id+'" data-inbox-force="true">'+tx('ah.0fb6c0','Neu erkennen')+'</button>':''}`;}
let inboxRenderedSignature='';
async function loadInbox(silent=false){
    if(inboxBulkDeleting)return;
    const version=++inboxLoadVersion;
    clearTimeout(inboxRefreshTimer);
    const list=$('#inboxList');if(!silent){inboxEditedItems.clear();list.textContent=tx('inbox.loading','Posteingang wird geladen …');}
    try{
        const [items,inboxLcs]=await Promise.all([json('/api/inbox'),json('/api/lcs')]);
        if(version!==inboxLoadVersion)return;
        if(items.some(inboxPending))inboxRefreshTimer=setTimeout(()=>{if(!$('#inboxSection').classList.contains('hidden'))loadInbox(true);},4000);
        // Never overwrite an in-progress assignment with a polling refresh.
        const signature=items.map(item=>item.id+'|'+item.extractionStatus+'|'+Boolean(item.automaticallySplit)).join(',');
        const unchanged=silent&&signature===inboxRenderedSignature&&list.querySelector('[data-inbox-id]');
        if(silent&&(unchanged||document.querySelector?.('dialog[open]')||inboxEditedItems.size||list.contains(document.activeElement)||list.querySelector('details[open]')||list.querySelector('[data-inbox-select]:checked'))){
            items.forEach(item=>{const status=list.querySelector(`[data-inbox-status="${item.id}"]`);if(status)status.innerHTML=inboxStatusHtml(item);const article=list.querySelector(`[data-inbox-id="${item.id}"]`);if(article?.dataset.pending==='true'&&!inboxPending(item)){article.querySelectorAll('form input,form select,form button:not([data-inbox-delete])').forEach(control=>control.disabled=false);article.dataset.pending='false';}});
            if(items.some(item=>item.automaticallySplit&&!list.querySelector(`[data-inbox-id="${item.id}"]`)))$('#inboxMessage').textContent=inboxAutoText('refresh','Automatically split documents are available. Refresh when your current edits are saved.');
            bindInboxRetry(list);
            return;
        }
        inboxRenderedSignature=signature;
        list.innerHTML=items.length?inboxBulkToolbar()+'<p class="inbox-limit">'+tx('inbox.openFiles','{n} offene Dateien',{n:items.length})+(items.length===100?' · '+tx('inbox.newest100','die neuesten 100 werden angezeigt'):'')+'</p>'+items.map(item=>renderInboxItem(item,inboxLcs)).join(''):'<div class="empty compact">'+tx('inbox.none','Keine offenen Dateien im Posteingang.')+'</div>';
        if(can('SETTINGS_MANAGE')){const manage=document.createElement('button');manage.type='button';manage.className='secondary';manage.textContent=tx('inbox.layouts','Layoutprofile verwalten');manage.onclick=openDocumentLayouts;list.prepend(manage);}
        if(items.length>1){const duplicates=document.createElement('button');duplicates.type='button';duplicates.className='secondary';duplicates.textContent=tx('inbox.duplicates','Identische Dateien prüfen');duplicates.onclick=()=>openDocumentDuplicates('/api/inbox/duplicates');list.prepend(duplicates);}
        list.querySelectorAll('[data-inbox-candidate]').forEach(button=>button.onclick=()=>{
            const form=list.querySelector(`[data-inbox-attach="${button.dataset.inboxItem}"]`),lc=inboxLcs.find(value=>value.id===button.dataset.inboxCandidate);
            if(!form||!lc){$('#inboxMessage').textContent=tx('inbox.suggestGone','Die vorgeschlagene Akte ist nicht mehr verfügbar. Bitte aktualisieren.');return;}
            form.elements.lcId.value=lc.id;form.elements.documentType.focus();
            $('#inboxMessage').textContent='Vorschlag '+lc.reference+tx('al.38',' ausgewählt. Dokumenttyp prüfen und Zuordnung bestätigen.');
        });
        list.querySelectorAll('[data-inbox-attach]').forEach(form=>form.onsubmit=async event=>{
            event.preventDefault();const lcId=form.elements.lcId.value,lc=inboxLcs.find(value=>value.id===lcId),item=items.find(value=>value.id===form.dataset.inboxAttach),button=form.querySelector('button[type="submit"]');
            if(!lc){$('#inboxMessage').textContent=tx('inbox.validLc','Bitte eine gültige LC-Akte auswählen.');return;}
            if(!await confirmAction(tx('at.784997','„{p0}“ der LC-Akte „{p1}“ als {p2} zuordnen?',{p0:item.originalFilename,p1:lc.reference,p2:form.elements.documentType.selectedOptions[0].textContent})))return;
            button.disabled=true;
            try{await json(`/api/inbox/${item.id}/attach`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(inboxAttachment(form,lcId))});const siblings=item.sourceInboxId?items.filter(other=>other.id!==item.id&&other.sourceInboxId===item.sourceInboxId).length:0;await loadInbox();$('#inboxMessage').textContent='Dokument in LC-Akte '+lc.reference+' gespeichert.'+(siblings?tx('at.dcd10b',' {p0} weitere Teile derselben Sammel-PDF sind jetzt mit dieser Akte vorbelegt – „Eindeutige zuordnen“ ordnet sie gemeinsam zu.',{p0:siblings}):'');}
            catch(error){$('#inboxMessage').textContent='Zuordnung fehlgeschlagen: '+error.message;button.disabled=false;}
        });
        list.querySelectorAll('[data-inbox-delete]').forEach(button=>button.onclick=async()=>{
            if(!await confirmAction(tx('inbox.confirmDelete','Diese Datei aus dem Posteingang löschen?')))return;button.disabled=true;
            try{await json(`/api/inbox/${button.dataset.inboxDelete}`,{method:'DELETE'});$('#inboxMessage').textContent=tx('inbox.deleted','Datei aus dem Posteingang gelöscht.');await loadInbox();}
            catch(error){$('#inboxMessage').textContent=tx('inbox.deleteFailed','Löschen fehlgeschlagen: ')+error.message;button.disabled=false;}
        });
        list.querySelectorAll('[data-inbox-id]').forEach(article=>{
            const item=items.find(value=>value.id===article.dataset.inboxId);
            restoreInboxReview(article.querySelector('[data-inbox-attach]'),item);
            const status=document.createElement('div');status.dataset.inboxStatus=item.id;status.innerHTML=inboxStatusHtml(item);article.querySelector('.inbox-item-head').after(status);
            if(!inboxPending(item)){
                const details=document.createElement('details'),summary=document.createElement('summary'),facts=document.createElement('div');
                summary.textContent=tx('inbox.metaEvidence','Erkannte Metadaten und Fundstellen');details.append(summary,facts);status.after(details);
                details.addEventListener('toggle',async()=>{
                    if(!details.open||details.dataset.loaded)return;details.dataset.loaded='true';facts.textContent=tx('common.loading','Wird geladen …');
                    try{
                        const fields=await json('/api/inbox/'+item.id+'/recognition-facts');facts.replaceChildren();
                        const labels={lcReference:'LC-Referenz',documentNumber:'Dokumentnummer',documentDate:'Dokumentdatum',originalCopy:'Original / Copy'};
                        for(const field of fields){const row=document.createElement('p'),pages=[...new Set((field.words||[]).map(word=>word.page))];row.textContent=(labels[field.name]||field.name)+': '+(field.value||'Nicht eindeutig erkannt')+' · '+(pages.length?'Seite '+pages.join(', '):tx('inbox.noEvidence','Keine eindeutige Fundstelle'))+' · Bitte prüfen';facts.append(row);}
                    }catch(error){delete details.dataset.loaded;facts.textContent=tx('inbox.metaFailed','Metadaten konnten nicht geladen werden: ')+error.message;}
                });
            }
            article.addEventListener('input',event=>{if(!event.target.matches('[data-inbox-select]'))inboxEditedItems.add(item.id);});article.addEventListener('change',event=>{if(!event.target.matches('[data-inbox-select]'))inboxEditedItems.add(item.id);});
            article.dataset.pending=String(inboxPending(item));
            if(item.sourceInboxId){const link=document.createElement('a');link.href=`/api/inbox/${item.sourceInboxId}/content#page=${item.sourceFromPage}`;link.target='_blank';link.rel='noopener';link.textContent=`Sammeloriginal ansehen · Seiten ${item.sourceFromPage}–${item.sourceToPage}`;article.querySelector('.inbox-item-head').after(link);}
            if(item.automaticallySplit){const note=document.createElement('p');note.textContent=inboxAutoText('origin','Automatically created from a combined PDF. Review the document type; original and page range are retained.');article.querySelector('.inbox-item-head').after(note);}
            if(item.contentType==='application/pdf'&&can('DOCUMENT_UPLOAD')&&!inboxPending(item)){const split=document.createElement('button');split.type='button';split.className='secondary inbox-split-action';split.textContent=inboxAutoText('button','Split …');split.onclick=()=>openInboxSplit(item);article.querySelector('.inbox-item-head').after(split);}
            if(inboxPending(item))article.querySelectorAll('form input,form select,form button:not([data-inbox-delete])').forEach(control=>control.disabled=true);
        });
        bindInboxRetry(list);
    }catch(error){if(version!==inboxLoadVersion)return;if(!silent)list.textContent=tx('inbox.loadFailed','Posteingang konnte nicht geladen werden')+': '+error.message;else $('#inboxMessage').textContent=tx('inbox.refreshStopped','Automatische Aktualisierung unterbrochen')+': '+error.message;}
}
function bindInboxRetry(list){list.querySelectorAll('[data-inbox-retry]').forEach(button=>button.onclick=async()=>{if(button.dataset.inboxForce&&!await confirmAction(tx('sp.0','Erkennung dieser Datei neu starten? Der erkannte Text wird ersetzt; bereits eingegebene Werte im Formular bleiben erhalten.')))return;button.disabled=true;try{await json(`/api/inbox/${button.dataset.inboxRetry}/retry${button.dataset.inboxForce?'?force=true':''}`,{method:'POST'});await loadInbox();}catch(error){$('#inboxMessage').textContent=error.message;button.disabled=false;}});}
function mergeInboxSplitRanges(previous,current){
    if(!previous||!current||![previous.fromPage,previous.toPage,current.fromPage,current.toPage].every(Number.isInteger)||previous.fromPage<1||previous.toPage<previous.fromPage||current.fromPage!==previous.toPage+1||current.toPage<current.fromPage)throw Error(tx('sp.1','Nur gültige, direkt angrenzende Bereiche können zusammengeführt werden.'));
    return {...previous,toPage:current.toPage};
}
function divideInboxSplitRange(part){
    if(!part||![part.fromPage,part.toPage].every(Number.isInteger)||part.fromPage<1||part.toPage<=part.fromPage)throw Error(tx('sp.2','Dieser Bereich enthält keine abtrennbare Seite.'));
    return [{...part,toPage:part.toPage-1},{...part,fromPage:part.toPage,documentType:'OTHER',copyNumber:null}];
}
function explodeInboxSplitRange(parts,index){
    const part=parts?.[index];
    if(!part||![part.fromPage,part.toPage].every(Number.isInteger)||part.fromPage<1||part.toPage<=part.fromPage)throw Error(tx('sp.3','Dieser Bereich besteht nur aus einer Seite und kann nicht weiter aufgelöst werden.'));
    const pages=[];for(let page=part.fromPage;page<=part.toPage;page++)pages.push({fromPage:page,toPage:page,documentType:'OTHER',copyNumber:null});
    return [...parts.slice(0,index).map(p=>({...p})),...pages,...parts.slice(index+1).map(p=>({...p}))];
}
function dissolveInboxSplitRange(parts,index){
    if(!Number.isInteger(index)||index<0||index>=parts.length||parts.length<2)throw Error(tx('sp.4','Der letzte Bereich kann nicht aufgelöst werden.'));
    const result=parts.map(part=>({...part})),neighbor=index>0?index-1:1;
    const a=result[Math.min(index,neighbor)],b=result[Math.max(index,neighbor)];
    const merged=mergeInboxSplitRanges(a,b);
    result[neighbor]={...result[neighbor],fromPage:merged.fromPage,toPage:merged.toPage};result.splice(index,1);return result;
}
function adjustInboxSplitRanges(parts,index,field,pageCount){
    const result=parts.map(part=>({...part}));
    if(!Number.isInteger(index)||index<0||index>=result.length||!['fromPage','toPage'].includes(field)||!Number.isInteger(pageCount))throw Error(tx('sp.5','Ungültiger Seitenbereich.'));
    const current=result[index];
    if(![current.fromPage,current.toPage].every(Number.isInteger)||current.fromPage<1||current.toPage<current.fromPage||current.toPage>pageCount)throw Error(tx('sp.6','Bitte gültige Seitenzahlen innerhalb des PDFs angeben.'));
    if(field==='fromPage'){
        if(index===0&&current.fromPage!==1)throw Error(tx('sp.7','Der erste Bereich muss auf Seite 1 beginnen.'));
        if(index>0){const previous=result[index-1];if(current.fromPage<=previous.fromPage)throw Error(tx('sp.8','Für den vorherigen Bereich muss mindestens eine Seite bleiben.'));previous.toPage=current.fromPage-1;}
    }else if(current.fromPage!==(index===0?1:result[index-1].toPage+1))throw Error(tx('sp.9','Bitte zuerst den Beginn dieses Bereichs korrigieren.'));
    for(let i=index+1;i<result.length;i++){
        const part=result[i];part.fromPage=result[i-1].toPage+1;
        if(!Number.isInteger(part.toPage))throw Error(tx('sp.10','Bitte gültige Seitenzahlen angeben.'));
        part.toPage=Math.max(part.fromPage,part.toPage);
        if(part.toPage>pageCount-(result.length-1-i))throw Error(tx('sp.11','Für die folgenden Bereiche bleiben nicht genügend Seiten. Bitte Bereiche zuerst verbinden oder entfernen.'));
    }
    if(result.at(-1).toPage!==pageCount)throw Error(tx('sp.12','Der letzte Bereich muss bis zur letzten PDF-Seite reichen.'));
    return result;
}
async function openSplitPretraining(){
    const picker=document.createElement('input');picker.type='file';picker.accept='application/pdf';
    picker.onchange=async()=>{const file=picker.files[0];if(!file)return;const data=new FormData();data.append('file',file);
        const button=$('#openSplitPretraining'),status=$('#splitPretrainingStatus');button.disabled=true;status.setAttribute('role','status');status.textContent=tx('sp.13','PDF wird hochgeladen …');
        try{
            let job=await json('/api/training/document-types/jobs',{method:'POST',body:data});const started=Date.now();
            while(['QUEUED','RUNNING'].includes(job.state)){
                status.textContent=job.message+' · '+Math.floor((Date.now()-started)/1000)+' Sekunden';
                await new Promise(resolve=>setTimeout(resolve,2000));
                job=await json(`/api/training/document-types/jobs/${encodeURIComponent(job.id)}`);
            }
            if(job.state!=='COMPLETED'||!job.result)throw new Error(job.message||'Vortraining fehlgeschlagen.');
            status.textContent=tx('sp.14','Erkennung abgeschlossen. Vorschläge bitte prüfen.');
            await openInboxSplit({pretraining:true,file,proposal:job.result.proposal,receipt:job.result.receipt});
        }catch(error){status.textContent=error.message;}
        finally{button.disabled=false;}
    };picker.click();
}
/** Why a page of a split proposal was recognised, and whether it is safe: {sure, text}. */
function inboxSplitPageReason(classification){
    const c=classification||{},method=String(c.method||'');
    const how=[[/^DOCUMENT_HEADINGS|^SWIFT_MT700|^LC_CORRESPONDENCE/,tx('sp.15','Überschrift')],[/^LC_EXPECTED/,tx('spx.0','vom LC gefordert')],[/^PAGE_SEQUENCE/,tx('sp.16','Seitenzählung')],[/^PAGE_TOTAL/,tx('sp.17','Seitenanzahl der Vorseite')],[/^DOCUMENT_REFERENCE/,tx('spx.1','gleiche Dokumentnummer')],[/^BLANK_PAGE/,tx('sp.18','Leer- oder Rückseite')],[/^KEYWORD_PROFILE/,tx('spx.2','typische Felder')],[/^CONFIRMED_SPLIT_PATTERN/,tx('sp.19','bestätigtes Training')],[/^TRAINED_LAYOUT/,tx('spx.3','bekanntes Layout')],[/^TRAINED_TYPE/,tx('sp.20','ähnlich bestätigten Beispielen')],[/^FILENAME_HINT/,tx('spx.4','nur Dateiname')]].find(([re])=>re.test(method));
    const evidence=Array.isArray(c.evidence)&&c.evidence.length?String(c.evidence[0]).replace(/\s+/g,' ').slice(0,60):'';
    const sure=!!c.suggestedType&&c.status!=='REVIEW'&&c.status!=='AMBIGUOUS'&&c.status!=='UNKNOWN';
    const reason=c.status==='AMBIGUOUS'?tx('sp.21','mehrere Dokumentarten möglich'):!c.suggestedType?tx('sp.22','nicht erkannt'):how?how[1]+(evidence&&/^(DOCUMENT_HEADINGS|SWIFT_MT700|LC_CORRESPONDENCE|LC_EXPECTED|TRAINED_LAYOUT)/.test(method)?` „${evidence}“`:''):'erkannt';
    return {sure,text:(sure?tx('spx.5','sicher: '):tx('sp.23','prüfen: '))+reason};
}
async function openInboxSplit(item){
    const originalUrl=item.pretraining?URL.createObjectURL(item.file):`/api/inbox/${item.id}/content`;
    const dialog=document.createElement('dialog');dialog.className='wide-dialog inbox-split-dialog';
    dialog.innerHTML='<p role="status"></p><button type="button"></button>';dialog.querySelector('p').textContent=inboxAutoText('checking','Checking page boundaries and document types …');dialog.querySelector('button').textContent=inboxAutoText('close','Close');
    dialog.querySelector('button').onclick=()=>dialog.close();dialog.onclose=()=>{if(item.pretraining)URL.revokeObjectURL(originalUrl);dialog.remove();};document.body.append(dialog);dialog.showModal();
    try{
        const proposal=item.proposal||await json(`/api/inbox/${item.id}/split-proposal`);if(!dialog.isConnected)return;
        if(proposal.pageCount<2)throw new Error(tx('sp.24','Diese PDF enthält nur eine Seite und kann nicht seitenweise aufgeteilt werden.'));
        dialog.innerHTML=`<div class="dialoghead"><h2>${tx('ai.8a5017','Sammel-PDF aufteilen')}</h2><button type="button" data-close>${tx('ah.8311b9','Schließen')}</button></div><div class="evidence-workspace"><section class="evidence-details"><h3>${tx('ai.9fdbbc','Hinweise')}</h3><p>${proposal.pageCount} Seiten · Vorschläge bitte prüfen. Das Original bleibt erhalten.</p><details><summary>${tx('ah.8d4cbd','Hinweise zur Aufteilung')}</summary><p>${tx('ah.a866b4','Jede Seite muss genau einmal enthalten sein. Zusammenhängende, nummerierte Fortsetzungsseiten werden gemeinsam vorgeschlagen und müssen geprüft werden. Nicht zuordenbare Seiten bleiben separat. Mit der Bestätigung werden Aufteilung und Dokumenttypen als Trainingsmuster für diesen Mandanten gespeichert. Automatische Aufteilungen lernen nicht.')}</p></details><details><summary>${tx('ah.1fb9f3','Erkennung je Seite')}</summary>${proposal.pages.map(page=>`<p>${tx('ah.4789ac','Seite')} ${page.number}: ${esc(inboxDocumentTypes.find(([type])=>type===page.classification.suggestedType)?.[1]||tx('inbox.unclear','Unklar'))} · ${page.classification.status==="REVIEW"?tx('b4.review','Prüfen')+' · ':''}${esc(page.classification.evidence.join(' · ')||tx('sp.25','Keine eindeutige Überschrift erkannt'))} ${esc(page.copyHint?.evidence||'')}${page.textSource==='UNAVAILABLE'?tx('sp.26','<br><small>'+tx('ah.4f5ed7','Kein Text gelesen – die Texterkennung hat für diese Seite nichts geliefert.')+'</small>'):page.sample?'<br><small>'+tx('ai.3bb1f0','Gelesen:')+' '+esc(page.sample)+'</small>':''}</p>`).join('')}</details><h3>${tx('ai.05b407','Bereiche')}</h3><form data-split-form><div data-parts></div><button type="button" class="secondary" data-add>${tx('ah.82abf5','Bereich hinzufügen')}</button><div class="split-submit-panel"><p data-error role="alert" tabindex="-1"></p><p data-submit-status role="status" aria-live="polite"></p><button type="submit">${tx('ah.201069','Aufteilung bestätigen')}</button></div></form></section><section class="evidence-document"><div class="evidence-page-toolbar"><button type="button" data-prev disabled aria-label="${tx('ah.22145e','Vorherige Seite')}">←</button><span data-page></span><button type="button" data-next disabled aria-label="${tx('ah.a48abf','Nächste Seite')}">→</button><button type="button" data-zoom>${tx('ah.8a6c98','Vergrößern')}</button><button type="button" data-retry>${tx('ah.6e5595','Neu laden')}</button><a class="button-link" target="_blank" rel="noopener" data-original-pdf href="/api/inbox/${item.id}/content">${tx('ah.353e11','Original separat öffnen')}</a></div><p data-status role="status"></p><div class="evidence-viewer"><nav class="evidence-pages" aria-label="${tx('ah.9c70ff','Seiten')}"></nav><div class="evidence-document-viewport"><img alt="${tx('ah.d73921','PDF-Seite')}" hidden></div></div></section></div>`;
        dialog.querySelector('[data-close]').onclick=()=>dialog.close();
        dialog.querySelector('[data-original-pdf]').href=originalUrl;
        if(item.pretraining){dialog.querySelector('h2').textContent='Sammel-PDF vortrainieren';dialog.querySelector('[type=submit]').textContent='Trainingsmuster speichern';const notice=document.createElement('p');notice.textContent='Nur Training: Es entstehen keine Posteingangsdokumente und keine LC-Akte. Die PDF wird nicht gespeichert. Bitte alle Dokumenttypen und Seitengrenzen fachlich prüfen.';dialog.querySelector('form').before(notice);}
        const rows=dialog.querySelector('[data-parts]'),form=dialog.querySelector('form');
        form.noValidate=true;
        const submitStatus=form.querySelector('[data-submit-status]');
        if(proposal.pages.some(page=>page.classification.method==='CONFIRMED_SPLIT_PATTERN_V1')){
            const learned=document.createElement('p');learned.setAttribute('role','status');
            learned.textContent=tx('sp.27','Vorschlag aus bestätigtem Training. Bitte Seitenbereiche und Dokumenttypen erneut prüfen.');form.before(learned);
        }
        const pageInfo=new Map(proposal.pages.map(page=>[page.number,inboxSplitPageReason(page.classification)]));
        const uncertainPages=new Set(proposal.pages.filter(page=>page.classification.status==='REVIEW'||!page.classification.suggestedType).map(page=>page.number));
        if(!item.pretraining&&!item.sourceInboxId&&proposal.pages.length>0){
            const clarity=document.createElement('div');clarity.className='split-clarity';clarity.setAttribute('role','status');
            if(uncertainPages.size===0){
                const text=document.createElement('p');text.textContent=inboxAutoText('clear','Proposal is unambiguous');
                const apply=document.createElement('button');apply.type='button';apply.textContent=inboxAutoText('apply','Accept proposal');
                apply.onclick=async()=>{apply.disabled=true;try{const parts=await json(`/api/inbox/${item.id}/auto-split`,{method:'POST'});if(!parts.length){text.textContent=inboxAutoText('review','No safe automatic split available. Please review the page ranges manually.');apply.disabled=false;return;}dialog.close();$('#inboxMessage').textContent=parts.length+' '+inboxAutoText('created','documents split automatically. Review their document types.');await loadInbox();}catch(error){text.textContent=error.message;apply.disabled=false;}};
                clarity.append(text,apply);
            }else{
                const text=document.createElement('p');text.textContent=inboxAutoText('uncertain','Uncertain pages (please review): ')+[...uncertainPages].join(', ');clarity.append(text);
            }
            form.before(clarity);
        }
        const images=new Map(),imageUrls=[],abort=new AbortController();let rendering=Promise.resolve(),zoomVersion=0;
        const oldClose=dialog.onclose;dialog.onclose=()=>{abort.abort();observer?.disconnect();imageUrls.forEach(url=>URL.revokeObjectURL(url));oldClose();};
        function pageImage(page,enlarged=false){
            const key=page+':'+enlarged;if(images.has(key))return images.get(key);
            const promise=rendering.catch(()=>{}).then(async()=>{
                if(!dialog.isConnected)throw new Error('Vorschau geschlossen.');
                let url=`/api/inbox/${encodeURIComponent(item.id)}/pages/${page}/preview?enlarged=${enlarged}`,options={signal:abort.signal};
                if(item.pretraining){url='/api/training/document-types/page-preview';const body=new FormData();body.append('file',item.file);body.append('page',page);body.append('enlarged',enlarged);options={...options,method:'POST',headers:{'X-CSRF-TOKEN':csrfToken},body};}
                const response=await fetch(url,options);
                if(!response.ok||!response.headers.get('Content-Type')?.startsWith('image/png'))throw new Error(tx('al.39','Seitenbild nicht verfügbar. Bitte erneut anklicken oder das Original öffnen.'));
                const imageUrl=URL.createObjectURL(await response.blob());if(!dialog.isConnected){URL.revokeObjectURL(imageUrl);throw new Error('Vorschau geschlossen.');}imageUrls.push(imageUrl);return imageUrl;
            });images.set(key,promise);rendering=promise;promise.catch(()=>images.delete(key));return promise;
        }
        const viewport=dialog.querySelector('.evidence-document-viewport'),image=viewport.querySelector('img'),status=dialog.querySelector('[data-status]'),pagesNav=dialog.querySelector('.evidence-pages');
        const prevButton=dialog.querySelector('[data-prev]'),nextButton=dialog.querySelector('[data-next]'),pageLabel=dialog.querySelector('[data-page]');
        let selectedPage=1;
        async function showPage(page){
            if(!Number.isInteger(page)||page<1||page>proposal.pageCount)return;
            const version=++zoomVersion;selectedPage=page;pageLabel.textContent=`Seite ${page} / ${proposal.pageCount}`;prevButton.disabled=page<=1;nextButton.disabled=page>=proposal.pageCount;
            dialog.querySelectorAll('.evidence-page-thumb').forEach(thumb=>{const on=Number(thumb.dataset.page)===page;thumb.classList.toggle('selected',on);if(on&&pagesNav.contains(thumb))thumb.scrollIntoView({block:'nearest'});});
            status.textContent=tx('at.5fe275','Seite {p0} wird geladen …',{p0:page});image.hidden=true;
            try{const url=await pageImage(page,true);if(version!==zoomVersion||!dialog.isConnected)return;image.src=url;image.alt=`PDF-Seite ${page}`;image.hidden=false;status.textContent='';}catch(error){if(version===zoomVersion&&dialog.isConnected)status.textContent=error.message;}
        }
        const enlargePage=(page)=>showPage(page);
        function loadThumb(button){if(button.dataset.loaded)return;button.dataset.loaded='true';pageImage(Number(button.dataset.page)).then(url=>{if(button.isConnected)button.querySelector('img').src=url;}).catch(()=>{if(button.isConnected)delete button.dataset.loaded;});}
        const observer=typeof IntersectionObserver==='function'?new IntersectionObserver(entries=>entries.forEach(entry=>{if(entry.isIntersecting){observer.unobserve(entry.target);loadThumb(entry.target);}}),{root:dialog,rootMargin:'100px'}):null;
        for(let page=1;page<=proposal.pageCount;page++){const thumb=document.createElement('button');thumb.type='button';thumb.className='evidence-page-thumb';thumb.dataset.page=page;thumb.setAttribute('aria-label',`Seite ${page}`);thumb.innerHTML=`<img alt="" decoding="async"><span>${page}</span><small></small>`;thumb.onclick=()=>{loadThumb(thumb);showPage(page);};pagesNav.append(thumb);if(observer)observer.observe(thumb);else loadThumb(thumb);}
        pagesNav.querySelectorAll('.evidence-page-thumb').forEach(thumb=>{const info=pageInfo.get(Number(thumb.dataset.page));if(info){thumb.classList.add(info.sure?'split-sure':'split-unsure');thumb.title=`Seite ${thumb.dataset.page} – ${info.text}`;}});
        prevButton.onclick=()=>showPage(selectedPage-1);nextButton.onclick=()=>showPage(selectedPage+1);
        dialog.querySelector('[data-zoom]').onclick=event=>{const zoomed=image.classList.toggle('zoomed');event.currentTarget.textContent=zoomed?'Verkleinern':tx('al.40','Vergrößern');};
        dialog.querySelector('[data-retry]').onclick=()=>showPage(selectedPage);
        /** Each page thumbnail says which range it currently belongs to. */
        function markRanges(){
            const marks=new Map();[...rows.children].forEach((row,index)=>{const part=readRange(row);for(let page=part.fromPage;page<=part.toPage;page++)marks.set(page,index+1);});
            pagesNav.querySelectorAll('.evidence-page-thumb').forEach(thumb=>{const range=marks.get(Number(thumb.dataset.page));thumb.querySelector('small').textContent=(range?`Bereich ${range}`:'–')+(pageInfo.get(Number(thumb.dataset.page))?.sure===false?' · prüfen':'');thumb.classList.toggle('has-other',!range);});
            [...rows.children].forEach((row,index)=>{const part=readRange(row),title=row.querySelector('.split-range-title');if(title)title.textContent=`Bereich ${index+1} · ${part.fromPage===part.toPage?`Seite ${part.fromPage}`:`Seiten ${part.fromPage}–${part.toPage}`}`;});
        }
        /** The pages of one range as small previews inside its card; a click shows the page large on the right. */
        function renderThumbs(row){
            if(row){
                let strip=row.querySelector('.split-thumbnails');if(!strip){strip=document.createElement('div');strip.className='split-thumbnails';row.querySelector('.split-row-actions').before(strip);}
                strip.querySelectorAll('button').forEach(button=>observer?.unobserve(button));strip.replaceChildren();const part=readRange(row);
                if(Number.isInteger(part.fromPage)&&Number.isInteger(part.toPage)&&part.fromPage>=1&&part.toPage<=proposal.pageCount&&part.toPage>=part.fromPage)
                    for(let page=part.fromPage;page<=part.toPage;page++){const thumb=document.createElement('button');thumb.type='button';thumb.className='evidence-page-thumb';thumb.dataset.page=page;thumb.setAttribute('aria-label',`Seite ${page} ansehen`);thumb.innerHTML=`<img alt="" decoding="async"><span>${tx('ah.4789ac','Seite')} ${page}</span>`;thumb.onclick=()=>{loadThumb(thumb);showPage(page);};strip.append(thumb);if(observer)observer.observe(thumb);else loadThumb(thumb);}
            }
            markRanges();
        }
        const readRange=row=>({fromPage:Number(row.querySelector('[name=fromPage]').value),toPage:Number(row.querySelector('[name=toPage]').value),documentType:row.querySelector('[name=documentType]').value,copyNumber:readDocumentCopy(row.querySelector('[name=copyNumber]').value)});
        const refreshMergeButtons=()=>{[...rows.children].forEach((row,index)=>row.querySelector('[data-merge]').disabled=index===0);markRanges();};
        rows.addEventListener('change',event=>{
            if(!['fromPage','toPage'].includes(event.target.name))return;
            const error=dialog.querySelector('[data-error]');error.textContent='';
            const all=[...rows.children];
            try{
                const adjusted=adjustInboxSplitRanges(all.map(readRange),all.indexOf(event.target.closest('.inbox-split-row')),event.target.name,proposal.pageCount);
                adjusted.forEach((part,index)=>{all[index].querySelector('[name=fromPage]').value=part.fromPage;all[index].querySelector('[name=toPage]').value=part.toPage;renderThumbs(all[index]);});
            }catch(failure){error.textContent=failure.message;}
        });
        const add=part=>{
            const row=document.createElement('div');row.className='inbox-split-row';
            row.innerHTML=`<h4 class="split-range-title"></h4><label>${tx('ah.74f813','Von Seite')}<input name="fromPage" type="number" min="1" max="${proposal.pageCount}" value="${part.fromPage}" required></label><label>${tx('ah.0e3a34','Bis Seite')}<input name="toPage" type="number" min="1" max="${proposal.pageCount}" value="${part.toPage}" required></label><label class="split-type">${tx('ah.950bb1','Dokumenttyp')}<select name="documentType" required>${inboxDocumentTypes.map(([type,label])=>`<option value="${type}"${type===part.documentType?' selected':''}>${label}</option>`).join('')}</select></label><label>${tx('ai.10eff1','Kennzeichnung')}<select name="copyNumber" ${item.pretraining?'disabled':''}>${documentCopyOptions(part.copyNumber)}</select></label>${item.pretraining?'':'<label class="split-date">'+tx('ah.3147c4','Datum, falls nicht erkannt')+'<input name="documentDate" type="date"></label><label class="split-signed"><input name="signed" type="checkbox"> '+tx('ai.2a3599','Unterschrift vorhanden')+'</label>'}<div class="split-row-actions"><button type="button" class="secondary" data-preview>${tx('ah.68c17c','Seite ansehen')}</button><button type="button" class="secondary" data-merge>${tx('ah.cb7261','Mit vorherigem verbinden')}</button><button type="button" class="secondary" data-remove>${tx('ah.b10291','Bereich auflösen (in Einzelseiten)')}</button></div>`;
            row.querySelector('[data-remove]').onclick=()=>{
                const error=dialog.querySelector('[data-error]');error.textContent='';
                const range=readRange(row);
                if(!Number.isInteger(range.fromPage)||range.toPage<=range.fromPage){error.textContent=tx('al.41','Dieser Bereich besteht nur aus einer Seite und kann nicht weiter aufgelöst werden.');return;}
                row.querySelector('.split-dissolve-confirm')?.remove();
                const confirmation=document.createElement('div');confirmation.className='split-dissolve-confirm';confirmation.innerHTML='<p></p><div class="split-row-actions"><button type="button" data-confirm>'+tx('ai.de6ce5','Ja, in Einzelseiten zerlegen')+'</button><button type="button" class="secondary" data-cancel>'+tx('ah.4b9727','Abbrechen')+'</button></div>';
                confirmation.querySelector('p').textContent=tx('at.f78618','Bereich {p0}–{p1} in {p2} Einzelseiten zerlegen? Jede Seite wird ein eigener Bereich mit Dokumenttyp „Sonstiges“, den Sie danach einzeln einordnen. Keine PDF-Seite wird gelöscht.',{p0:range.fromPage,p1:range.toPage,p2:range.toPage-range.fromPage+1});
                confirmation.querySelector('[data-cancel]').onclick=()=>{confirmation.remove();row.querySelector('[data-remove]').focus({preventScroll:true});};
                confirmation.querySelector('[data-confirm]').onclick=()=>{
                    try{
                        const all=[...rows.children],index=all.indexOf(row),exploded=explodeInboxSplitRange(all.map(readRange),index);
                        rows.replaceChildren();exploded.forEach(add);
                        refreshMergeButtons();
                        const first=rows.children[index];first?.querySelector('[name=fromPage]').focus({preventScroll:true});submitStatus.textContent=tx('al.42','Bereich in Einzelseiten zerlegt. Bitte Dokumenttypen prüfen.');
                    }catch(failure){error.textContent=failure.message;confirmation.querySelector('p').textContent=failure.message;}
                };
                row.append(confirmation);confirmation.querySelector('[data-cancel]').focus({preventScroll:true});confirmation.scrollIntoView({behavior:'smooth',block:'nearest'});
            };
            row.querySelector('[data-preview]').onclick=()=>{showPage(readRange(row).fromPage);};
            row.querySelector('[data-merge]').onclick=async()=>{const error=dialog.querySelector('[data-error]');error.textContent='';try{const previous=row.previousElementSibling,a=readRange(previous),b=readRange(row),merged=mergeInboxSplitRanges(a,b);if((a.documentType!==b.documentType||a.copyNumber!==b.copyNumber)&&!await confirmAction('Unterschiedliche Dokumenttypen oder Original/Copy-Kennzeichnungen verbinden? Dokumenttyp und Kennzeichnung des vorherigen Bereichs bleiben erhalten. Bitte anschließend prüfen.'))return;previous.querySelector('[name=toPage]').value=merged.toPage;row.remove();refreshMergeButtons();renderThumbs(previous);}catch(failure){error.textContent=failure.message;}};
            rows.append(row);refreshMergeButtons();renderThumbs(row);
        };
        proposal.parts.forEach(add);
        markRanges();showPage(1);
        [...rows.children].forEach(row=>{const range=readRange(row);for(let page=range.fromPage;page<=range.toPage;page++)if(uncertainPages.has(page)){row.classList.add('split-uncertain');const mark=document.createElement('p');mark.className='split-uncertain-note';mark.textContent=inboxAutoText('uncertainRow','Uncertain – please review');row.querySelector('.split-range-title').after(mark);break;}});
        // Why each range was proposed; ranges made only of safe pages count as accepted and can be hidden to focus on the uncertain ones.
        let safeRanges=0;
        [...rows.children].forEach(row=>{
            const range=readRange(row),lines=[];let safe=true;
            for(let page=range.fromPage;page<=range.toPage;page++){const info=pageInfo.get(page);if(!info)continue;if(!info.sure)safe=false;lines.push(`S. ${page} ${info.text}`);}
            if(lines.length){const note=document.createElement('p');note.className='split-reason';note.textContent=lines.join(' · ');(row.querySelector('.split-row-actions')||row.lastChild).before(note);}
            if(safe&&!row.classList.contains('split-uncertain')){row.classList.add('split-safe');safeRanges++;}
        });
        const clarityBox=dialog.querySelector('.split-clarity');
        if(clarityBox&&uncertainPages.size&&safeRanges){
            const summary=document.createElement('p');summary.textContent=tx('at.ba059d','{p0} von {p1} Bereichen sind sicher erkannt und werden so übernommen.',{p0:safeRanges,p1:rows.children.length});
            const toggle=document.createElement('button');toggle.type='button';toggle.className='secondary';toggle.textContent='Nur unsichere Bereiche zeigen';
            toggle.onclick=()=>{const only=rows.classList.toggle('only-uncertain');toggle.textContent=only?tx('al.43','Alle Bereiche zeigen'):'Nur unsichere Bereiche zeigen';};
            clarityBox.append(summary,toggle);
        }
        dialog.querySelector('[data-add]').onclick=()=>{
            const error=dialog.querySelector('[data-error]');error.textContent='';
            const row=[...rows.children].reverse().find(row=>readRange(row).toPage>readRange(row).fromPage);
            if(!row){error.textContent=tx('al.44','Jede Seite hat bereits einen eigenen Bereich. Zum Zusammenfassen „Mit vorherigem verbinden“ verwenden.');return;}
            const [retained,created]=divideInboxSplitRange(readRange(row));row.querySelector('[name=toPage]').value=retained.toPage;
            add(created);row.after(rows.lastElementChild);refreshMergeButtons();renderThumbs(row);
        };
        form.onsubmit=async event=>{event.preventDefault();const error=dialog.querySelector('[data-error]');error.textContent='';
            submitStatus.textContent=tx('al.45','Seitenbereiche werden geprüft …');
            const invalid=[...form.querySelectorAll('input,select')].find(input=>!input.disabled&&!input.checkValidity());
            if(invalid){submitStatus.textContent=tx('al.46','Nicht übernommen.');error.textContent=tx('al.47','Bitte die markierte Seitenzahl oder den Dokumenttyp korrigieren.');invalid.focus();invalid.reportValidity();return;}
            const parts=[...rows.children].map(row=>({fromPage:Number(row.querySelector('[name=fromPage]').value),toPage:Number(row.querySelector('[name=toPage]').value),documentType:row.querySelector('[name=documentType]').value,copyNumber:readDocumentCopy(row.querySelector('[name=copyNumber]').value)}));
            let next=1;for(const [index,part] of parts.entries()){if(!Number.isInteger(part.fromPage)||!Number.isInteger(part.toPage)||part.fromPage!==next||part.toPage<next||part.toPage>proposal.pageCount){submitStatus.textContent=tx('al.46','Nicht übernommen.');error.textContent=tx('at.03d4ae','Bereich {p0}: erwartet wird ein Beginn bei Seite {p1}; angegeben ist {p2}–{p3}. Bitte Lücken und Überschneidungen korrigieren. Keine Daten wurden übernommen.',{p0:index+1,p1:next,p2:part.fromPage,p3:part.toPage});rows.children[index].querySelector('[name=fromPage]').focus();return;}next=part.toPage+1;}
            if(parts.length<1||parts.length>100||next!==proposal.pageCount+1){submitStatus.textContent=tx('al.46','Nicht übernommen.');error.textContent=tx('al.48','Bitte 1 bis 100 Bereiche angeben und alle Seiten abdecken.');error.focus();return;}
            const submit=form.querySelector('[type=submit]');submit.disabled=true;
            const label=submit.textContent;submit.textContent=tx('al.49','Wird übernommen …');submitStatus.textContent=item.pretraining?tx('al.50','Trainingsmuster wird gespeichert …'):parts.length===1?tx('al.51','Dokumentbereich wird bestätigt und gespeichert …'):tx('al.52','Teil-Dokumente werden erstellt und gespeichert …');
            if(item.pretraining){try{await json('/api/training/document-types/confirm',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({receipt:item.receipt,parts})});dialog.close();$('#splitPretrainingStatus').textContent=tx('al.53','Trainingsmuster gespeichert. Es steht allen berechtigten Benutzern dieses Mandanten zur Verfügung.');}catch(failure){submitStatus.textContent=tx('al.54','Übernahme fehlgeschlagen.');error.textContent=failure.message;error.focus();submit.disabled=false;submit.textContent=label;}return;}
            try{const result=await json(`/api/inbox/${item.id}/split`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({parts,details:[...rows.children].map(row=>({documentDate:row.querySelector('[name=documentDate]')?.value||null,signed:!!row.querySelector('[name=signed]')?.checked}))})});dialog.close();$('#inboxMessage').textContent=result.length===1?tx('al.55','Dokumentbereich bestätigt. Das Original bleibt über das Dokument erreichbar.'):tx('at.3cfc7c','{p0} Teil-Dokumente erstellt. Das Sammeloriginal ist über die Teil-Dokumente weiterhin erreichbar.',{p0:result.length});await loadInbox();}
            catch(failure){submitStatus.textContent=tx('al.54','Übernahme fehlgeschlagen.');error.textContent=failure.message;error.focus();submit.disabled=false;submit.textContent=label;}
        };
    }catch(error){if(dialog.isConnected)dialog.querySelector('p').textContent=error.message;}
}
document.addEventListener('click',event=>{
    const button=event.target.closest?.('[data-pdf-prev],[data-pdf-next]');if(!button)return;
    const box=button.closest('[data-pdf-page]'),image=box?.querySelector('img'),url=image?.getAttribute('src')||image?.dataset.previewSrc;if(!url)return;
    const current=Number(box.dataset.pdfPage),target=current+(button.hasAttribute('data-pdf-next')?1:-1);if(target<1)return;
    const next=url.replace(/\/pages\/\d+\//,`/pages/${target}/`);
    image.onerror=()=>{image.onerror=null;image.src=url;box.querySelector('[data-pdf-next]').disabled=true;};
    image.onload=()=>{box.dataset.pdfPage=String(target);box.querySelector('[data-pdf-label]').textContent=`Seite ${target}`;box.querySelector('[data-pdf-prev]').disabled=target<=1;box.querySelector('[data-pdf-next]').disabled=false;};
    image.src=next;
});
document.addEventListener('click',async event=>{
    const button=event.target.closest?.('#reparseRequirements');if(!button||!activeLc||button.disabled)return;
    const status=document.querySelector('#reparseStatus');button.disabled=true;status.textContent='';
    try{
        const preview=await json(`/api/lcs/${activeLc.id}/required-documents/reparse-preview`);
        if(!preview.changed){status.textContent=preview.reason;return;}
        if(!await confirmAction(tx('at.ee4cf4','{p0} Zeilen werden zu {p1} Dokumentenbedingungen zusammengeführt. Zuordnungen einzelner Bedingungen zu Dokumenttypen müssen danach gegebenenfalls neu gesetzt werden. Fortfahren?',{p0:preview.current.length,p1:preview.proposed.length})))return;
        await json(`/api/lcs/${activeLc.id}/required-documents/reparse`,{method:'POST'});
        await load();await show(activeLc.id);
    }catch(failure){status.textContent=failure.message;}finally{button.disabled=false;}
});
