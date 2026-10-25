/* Arbeitsplatz: Training mit Geschäftsimport, Nächster Schritt und Cockpit, Prüfarbeitsplätze, Monitoring, Mitgliederübersicht, Start. Teil der früheren app.js; die Teile teilen den globalen Gültigkeitsbereich und werden in dieser Reihenfolge geladen (siehe index.html). */
const renderTrainingWithBusinessImport=renderTraining;
renderTraining=function(){
    renderTrainingWithBusinessImport();
    setupTrainingPreviewSizes();
    $('#trainingModeNotice')?.remove();
    $('#trainingStatus').insertAdjacentHTML('afterbegin','<div id="trainingModeNotice" class="template-help"><b>'+tx('ai.4f7087','Trainingsmodus — Erkennung verbessern')+'</b><p>'+tx('ah.675129','„Nur Training abschließen“ speichert die geprüften Trainingsdaten, ohne eine LC-Akte anzulegen oder zu ändern. Die Qualitätsampel bewertet die Eignung für den Geschäftsimport, nicht den Trainingsabschluss.')+'</p></div>');
    let finish=$('#finishTrainingOnly');
    if(!finish){finish=document.createElement('button');finish.id='finishTrainingOnly';finish.type='button';finish.textContent=tx('tr.onlyTraining','Nur Training abschließen');$('#confirmTraining').before(finish);finish.onclick=finishTrainingOnly;}
    finish.classList.toggle('hidden',trainingItem.status==='CONFIRMED');
    $('#confirmTraining').classList.add('secondary');
    $('#confirmTraining').textContent=tx('tr.withBusiness','Training abschließen und Geschäftsdaten übernehmen');
    $('#confirmTraining').classList.toggle('hidden',trainingItem.status==='CONFIRMED'||trainingItem.preview.messageType==='MT760');
    $('#confirmTraining').onclick=()=>{if(confirm(tx('tr.confirm','Damit werden Geschäftsdaten übernommen: Ein LC wird angelegt/verknüpft oder ein Amendment importiert. Fortfahren?')))confirmTraining();};
};
async function finishTrainingOnly(){
    if(!trainingItem?.sessionId)return;
    const button=$('#finishTrainingOnly');button.disabled=true;
    clearTimeout(trainingSaveTimer);clearTimeout(trainingQualityTimer);
    try{
        await json(`/api/training/${trainingItem.sessionId}/finish`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(trainingDraftPayload())});
        trainingItem.status='CONFIRMED';
        await openTrainingSession(trainingItem.sessionId);
        $('#trainingError').textContent='';
        $('#trainingModeNotice').textContent=tx('al.145','Training abgeschlossen. Es wurden keine Geschäftsdaten angelegt oder geändert. Export und Ansicht bleiben über die Trainingshistorie verfügbar.');
        await loadDashboardDrafts();
    }catch(error){$('#trainingError').textContent=error.message;}
    finally{button.disabled=false;}
}
const openTrainingSeparated=openTraining;
openTraining=function(){openTrainingSeparated();$('#finishTrainingOnly')?.classList.add('hidden');$('#confirmTraining').textContent=tx('al.146','Geschäftsdaten übernehmen');};

function setupTrainingPreviewSizes(){
    document.querySelectorAll('.training-field').forEach(row=>{
        const text=row.querySelector('textarea').value;
        const expanded=text.length>400||text.split(/\r?\n/).length>6;
        const frame=row.querySelector('.snippet-frame');
        frame.id='trainingSnippet-'+row.dataset.index;
        frame.tabIndex=0;
        frame.setAttribute('role','region');
        frame.setAttribute('aria-label',tx('al.147','PDF-Ausschnitt; lange Inhalte können gescrollt werden'));
        const button=document.createElement('button');
        button.type='button';button.className='secondary preview-size-toggle';
        button.setAttribute('aria-controls',frame.id);
        const setExpanded=value=>{
            row.classList.toggle('preview-expanded',value);
            button.setAttribute('aria-expanded',String(value));
            button.textContent=value?'Kompakte Vorschau':tx('al.148','Große Vorschau');
        };
        button.onclick=()=>setExpanded(!row.classList.contains('preview-expanded'));
        row.querySelector('.pdf-context').append(button);
        setExpanded(expanded);
    });
}

function lcNextStep(lc,documents,review,tasks,deadlines){
    const open=tasks==null?0:tasks.filter(task=>!task.completed).length;
    const soon=(deadlines||[]).filter(item=>item.date&&daysUntil(item.date)>=0&&daysUntil(item.date)<=14).sort((a,b)=>String(a.date).localeCompare(String(b.date)))[0];
    if(review&&review.discrepancies>0)return [tx('ak.next.disc','{n} Abweichungen prüfen',{n:review.discrepancies}),'checks'];
    if(!lc.assignedTo)return [tx('ak.next.assign','Bearbeiter zuweisen'),'admin'];
    if(open>0)return [tx('ak.next.tasks','{n} offene Aufgaben erledigen',{n:open}),'tasks'];
    if(documents&&!documents.length)return [tx('ak.next.upload','Dokumente hochladen'),'documents'];
    if(soon)return [tx('ak.next.deadline','Frist am {date} beachten: {title}',{date:fmtDate(soon.date),title:soon.title}),'deadlines'];
    return [tx('ak.next.none','Keine offenen Punkte – Akte ist auf Stand.'),null];
}
function lcCockpitCards(lc,documents,review,tasks,deadlines){
    return [
        [tx('ak.parties','Beteiligte'),lc.beneficiary||tx('ak.notCaptured','Nicht erfasst'),tx('ak.partiesNote','Begünstigter · Antragsteller: {applicant}',{applicant:lc.applicant||tx('ak.notCaptured','Nicht erfasst')}),'admin'],
        [tx('ak.amount','Betrag'),money(lc.amount,lc.currency),statusLabel(lc.status||'RECEIVED'),'admin'],
        [tx('ak.cardDocuments','Dokumentenakte'),documents==null?tx('ak.na','Nicht verfügbar'):tx('ak.docsCount','{n} Dokumente',{n:documents.length}),tx('ak.openDocs','Dokumente und Anlagen öffnen'),'documents'],
        [tx('ak.cardPrecheck','Automatische Vorprüfung'),review==null?tx('ak.na','Nicht verfügbar'):tx('ak.precheckCount','{d} Abweichungen · {w} Hinweise',{d:review.discrepancies,w:review.warnings}),tx('ak.finalCheck','Fachliche Endprüfung bleibt erforderlich'),'checks'],
        [tx('ak.tasks','Aufgaben'),tasks==null?tx('ak.na','Nicht verfügbar'):tx('ak.tasksOpen','{n} offen',{n:tasks.filter(task=>!task.completed).length}),tx('ak.assignee','Bearbeiter: {name}',{name:lc.assignedTo||tx('ak.unassigned','Nicht zugewiesen')}),'tasks'],
        [tx('ak.deadlines','Fristen'),deadlines==null?tx('ak.na','Nicht verfügbar'):tx('ak.deadlinesCount','{n} berechnete Termine',{n:deadlines.length}),tx('ak.deadlinesNote','Ablauf, Versand, Vorlagefrist und gegebenenfalls Fälligkeit'),'deadlines']
    ];
}
const showWithOperationalCockpit=show;
show=async function(id){
    synchronizeExaminationLc(id);
    await showWithOperationalCockpit(id);
    if(activeLc?.id!==id)return;
    if(!receivingSynchronizedLcSelection)examinationWorkspaceChannel?.postMessage({type:'LC_SELECTED',lcId:id,source:examinationWorkspaceTabId});
    const overview=$('#detail .dossier-section[data-section="overview"]');
    if(!overview)return;
    overview.querySelector('.lc-operational-cockpit')?.remove();
    const block=document.createElement('div');block.className='lc-operational-cockpit';
    block.setAttribute('aria-live','polite');block.textContent=tx('ak.loading','Aktenüberblick wird geladen …');overview.prepend(block);
    const [documents,review,tasks,timeline,deadlines]=await Promise.all([
        json(`/api/lcs/${id}/documents`).catch(()=>null),
        json(`/api/lcs/${id}/document-checks`).catch(()=>null),
        json(`/api/lcs/${id}/tasks`).catch(()=>null),
        json(`/api/lcs/${id}/timeline`).catch(()=>null),
        json(`/api/lcs/${id}/deadlines`).catch(()=>null)
    ]);
    if(activeLc?.id!==id||!block.isConnected)return;
    const next=lcNextStep(activeLc,documents,review,tasks,deadlines);block.innerHTML=`<div class="next-step${next[1]?'':' done'}"${next[1]?` role="button" tabindex="0" data-cockpit-section="${next[1]}"`:''}><small>${tx('ak.next.label','Nächster Schritt')}</small><b>${esc(next[0])}</b></div><h3>${tx('ak.cockpit','LC-Cockpit')}</h3><div class="grid">`+lcCockpitCards(activeLc,documents,review,tasks,deadlines).map(([title,value,note,section])=>`<div class="card cockpit-card" role="button" tabindex="0" data-cockpit-section="${section}" aria-label="${esc(tx('ak.openSection','{title} öffnen',{title}))}"><small>${esc(title)}</small><b>${esc(value)}</b><p>${esc(note)}</p></div>`).join('')+'</div>';
    block.querySelectorAll('[data-cockpit-section]').forEach(button=>{button.onclick=()=>activateDossierSection(button.dataset.cockpitSection);button.onkeydown=event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();button.click()}}});
    const duplicates=document.createElement('button');duplicates.type='button';duplicates.className='secondary lc-duplicates';duplicates.textContent=tx('ak.duplicates','Identische Dokumentdateien prüfen');duplicates.onclick=()=>openDocumentDuplicates('/api/lcs/'+id+'/documents/duplicates');block.append(duplicates);
    const activity=document.createElement('section');
    activity.innerHTML=`<div class="sectionhead"><h3>${tx('ak.recent','Letzte Aktivitäten')}</h3><button type="button" class="secondary">${tx('ak.fullHistory','Gesamten Verlauf öffnen')}</button></div><div class="timeline-list">`+(timeline==null?`<p>${tx('ak.historyFailed','Der Verlauf konnte nicht geladen werden. Bitte die Akte erneut öffnen.')}</p>`:timelineHtml(timeline.slice(0,5)))+'</div>';
    activity.querySelector('button').onclick=()=>activateDossierSection('history');
    block.append(activity);
    globalThis.LcSla?.render(block,id);globalThis.LcPayments?.render(block,id);globalThis.LcGuarantees?.render(block,id);
    const deadlineSection=document.createElement('section');deadlineSection.className='dossier-section';deadlineSection.dataset.section='deadlines';
    deadlineSection.innerHTML=`<div class="sectionhead"><div><h3>${tx('ak.monitor','Fristenmonitor')}</h3><p>${tx('ak.monitorText','Automatisch abgeleitete Termine; markierte Fälligkeitsschätzungen bitte fachlich prüfen.')}</p></div><a class="button-link secondary" href="/api/lcs/${encodeURIComponent(activeLc.id)}/calendar.ics" download title="${tx('ak.icsTitle','Kalenderdatei mit Erinnerungen vor Ablauf, Verladetag und Vorlagefrist')}">${tx('ak.ics','In Kalender übernehmen (.ics)')}</a></div>`+(deadlines==null?`<p>${tx('ak.deadlinesFailed','Fristen konnten nicht geladen werden.')}</p>`:deadlines.length?'<div class="grid">'+deadlines.map(item=>`<div class="card"><small>${esc(item.title)}</small><b>${esc(fmtDate(item.date))}${item.confirmedBasis?'':' · '+tx('ak.estimate','Schätzung')}</b><p>${esc(item.source)}<br>${esc(item.note)}</p></div>`).join('')+'</div>':`<p>${tx('ak.noDeadlines','Aus den erfassten LC-Daten und Dokumenten wurden keine weiteren Termine abgeleitet.')}</p>`);
    const dossierContent=$('#detail .dossier-content'),dossierNav=$('#detail .dossier-nav');
    const deadlinesActive=!!dossierNav.querySelector('button[data-section="deadlines"].active');
    dossierContent.querySelectorAll('.dossier-section[data-section="deadlines"]').forEach(section=>section.remove());
    dossierNav.querySelectorAll('button[data-section="deadlines"]').forEach(button=>button.remove());
    dossierContent.append(deadlineSection);
    const deadlineNav=document.createElement('button');deadlineNav.type='button';deadlineNav.dataset.section='deadlines';deadlineNav.textContent=tx('ak.deadlines','Fristen');deadlineNav.onclick=()=>activateDossierSection('deadlines');dossierNav.append(deadlineNav);
    if(deadlinesActive)activateDossierSection('deadlines');
    if(examinationWorkspacePanel==='documents'||examinationWorkspacePanel==='checks')activateDossierSection(examinationWorkspacePanel);
};

function positionExaminationWorkspaceWindows(windows){
    if(typeof window.getScreenDetails!=='function')return;
    window.getScreenDetails().then(details=>{
        const screens=(details.screens||[]).filter(screen=>!screen.isPrimary);
        windows.forEach((handle,index)=>{const screen=screens[index];if(!screen||handle.closed)return;try{handle.moveTo(screen.availLeft??screen.left,screen.availTop??screen.top);handle.resizeTo(screen.availWidth??screen.width,screen.availHeight??screen.height)}catch(error){/* Browser or OS may disallow programmatic positioning. */}});
    }).catch(()=>{/* The standard draggable-window fallback remains available. */});
}
function setupWorkspacePresets(){
    const control=document.createElement('div');control.className='workspace-control';control.innerHTML='<small data-i18n="ws.label">'+tx('ai.bf49ac','ARBEITSPLATZ')+'</small><select id="workspacePreset" aria-label="'+tx('ah.0d2ba8','Arbeitsplatz auswählen')+'"><option value="standard" data-i18n="ws.standard">'+tx('ai.eb6d8a','Standard')+'</option><option value="examination" data-i18n="ws.examination">'+tx('ah.df24a5','LC-Prüfung')+'</option><option value="professional" data-i18n="ws.professional">'+tx('ai.f59d27','Drei Bildschirme')+'</option></select><button type="button" id="applyWorkspacePreset" data-i18n="ws.open">'+tx('ah.3e74a3','Arbeitsplatz öffnen')+'</button><small id="workspacePresetHint" aria-live="polite"></small>';
    $('#appNav').insertBefore(control,$('.app-nav-footer'));
    const select=$('#workspacePreset'),hint=$('#workspacePresetHint'),lcButton=document.querySelector('[data-app-section="lcs"]'),cockpitButton=document.querySelector('[data-app-section="cockpit"]'),childWindow=examinationWorkspacePanel==='documents'||examinationWorkspacePanel==='checks';
    if(childWindow){control.classList.add('hidden');select.value='professional';lcButton.click();return;}
    let saved='standard';try{const candidate=localStorage.getItem(workspacePresetStorageKey);if(['standard','examination','professional'].includes(candidate))saved=candidate}catch(error){}
    select.value=saved;
    if(saved==='standard')cockpitButton.click();else lcButton.click();
    if(saved==='professional')hint.textContent=tx('ws.saved','Gespeichert. Erneut öffnen, um Zusatzfenster zu starten.');
    $('#applyWorkspacePreset').onclick=()=>{
        const preset=select.value;try{localStorage.setItem(workspacePresetStorageKey,preset)}catch(error){}
        if(preset==='standard'){cockpitButton.click();hint.textContent=tx('ws.standardOpened','Standardansicht geöffnet.');return;}
        lcButton.click();
        if(preset==='examination'){hint.textContent=tx('ws.fileOpened','LC-Aktenansicht geöffnet. Dokumente lassen sich separat öffnen.');if(activeLc)show(activeLc.id);return;}
        const openWorkspace=(panel,name)=>{
            const existing=examinationWorkspaceWindows.find(item=>item.panel===panel&&!item.handle.closed);
            if(existing){existing.handle.focus();return existing.handle;}
            const url=new URL('/',location.href);url.searchParams.set('workspace',panel);
            const handle=window.open(url.toString(),name,'popup,width=1100,height=850,resizable=yes,scrollbars=yes');
            if(handle){try{handle.opener=null}catch(error){}examinationWorkspaceWindows.push({panel,handle});}
            return handle;
        };
        const documents=openWorkspace('documents','clm-examination-documents');
        const checks=openWorkspace('checks','clm-examination-checks');
        if(!documents||!checks){hint.textContent=tx('ws.blocked','Ein Zusatzfenster wurde blockiert. Bitte Pop-ups erlauben; geöffnete Fenster können Sie manuell auf weitere Bildschirme ziehen.');return;}
        hint.textContent=tx('ws.examOpened','Prüfung, Dokumente und Befunde geöffnet. Fenster bei Bedarf auf die Bildschirme ziehen.');
        positionExaminationWorkspaceWindows([documents,checks]);
        if(activeLc)show(activeLc.id);
    };
}

async function loadMonitoring(){
    const container=$('#monitoringMetrics');if(!container)return;
    container.innerHTML='<div class="empty compact">'+tx('ah.b5dcbb','Monitoring wird geladen …')+'</div>';
    try{
        const summary=await json('/api/admin/monitoring/summary');
        const metrics=[[tx('al.149','Offene Prüfungen'),summary.openExaminations],['Warten auf Kunde',summary.waitingForCustomer],['Abweichungen',summary.discrepancies],['Fristen ≤ 30 Tage',summary.upcomingDeadlines],['Freigabevorrat',summary.approvalQueue],['EBICS-Fehler',summary.ebicsErrors],['SWIFT-Fehler',summary.swiftErrors],['Fehlgeschlagene Hintergrundjobs',summary.failedBackgroundJobs]];
        container.innerHTML=metrics.map(([label,metric])=>`<article class="card ${metric.available?'':'monitoring-unavailable'}"><small>${esc(label)}</small><b>${metric.available?esc(metric.value):tx('al.150','Nicht verfügbar')}</b><p>${esc(metric.description)}</p></article>`).join('');
        $('#monitoringGeneratedAt').textContent='Stand: '+new Intl.DateTimeFormat(globalThis.LcI18n?.locale()||'en-GB',{dateStyle:'medium',timeStyle:'short'}).format(new Date(summary.generatedAt));
    }catch(error){container.innerHTML=`<div class="error">${tx('ah.f80aa2','Operationsmonitor konnte nicht geladen werden:')} ${esc(error.message)}</div>`;$('#monitoringGeneratedAt').textContent=''}
}
document.addEventListener('click', event => {
  if (event.target.closest('[data-close-upload-dialog]')) {
    document.querySelector('#uploadDialog').close();
  }
});

/* Membership overview */
function renderMembershipAssignment(body,roles){
 const form=document.createElement('form');form.className='membership-role-controls';
 const username=document.createElement('input');username.required=true;username.maxLength=100;username.placeholder=membershipText('tenant.existingUsername','Existing username');username.setAttribute('aria-label',username.placeholder);
 const picker=globalThis.LcMembersPage.accessPicker(globalThis.LcWorkspaceModules?globalThis.LcWorkspaceModules.corporate!==false:true);
const button=membershipNode('button',membershipText('tenant.assign','Assign existing user'));button.type='submit';
 const error=membershipNode('small','','error');error.setAttribute('role','alert');let busy=false;
 form.append(username,picker.element,button,error);body.append(membershipNode('h4',membershipText('tenant.assignHeading','Assign an existing user')),form);
 globalThis.LcTenantInvitations?.render?.(body); // inviting new people by e-mail comes first, assigning an existing account by name is the exception
 form.onsubmit=async event=>{event.preventDefault();if(busy||!can('USER_MANAGE'))return;busy=true;button.disabled=true;error.textContent='';
  try{await json('/api/users/memberships/shared',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:username.value.trim(),roleId:await picker.resolveRoleId()})});await globalThis.LcMembersPage?.reload?.();}
  catch(failure){error.textContent=failure.message;}finally{busy=false;button.disabled=false;}
 };
}
function membershipText(key,fallback){const value=globalThis.LcI18n?.t(key);return value&&value!==key?value:fallback;}
function membershipNode(tag,text,className){const node=document.createElement(tag);node.textContent=text;if(className)node.className=className;return node;}
function membershipMessage(key,fallback){const node=membershipNode('p',membershipText(key,fallback));node.dataset.i18n=key;return node;}
function membershipPermissionLabel(permission){const labels={LC_EDIT:'Edit LC files',LC_DELETE:'Delete LC files',SWIFT_IMPORT:'Import SWIFT messages',DOCUMENT_UPLOAD:'Upload documents',DOCUMENT_DELETE:'Delete documents',DOCUMENT_GENERATE:'Create documents (Maker)',DOCUMENT_REVIEW:'Review documents (Checker)',DOCUMENT_APPROVE:'Approve documents (Approver)',EMAIL_SEND:'Send emails from LC files',TRAINING_MANAGE:'Manage training',USER_MANAGE:'Manage users',AUDIT_VIEW:'View audit log',SETTINGS_MANAGE:'Manage companies and document templates'};return Object.hasOwn(labels,permission)?membershipText('permission.'+permission,labels[permission]):String(permission);}
function membershipRolePermissions(roles,id){const permissions=roles.find(role=>role.id===id)?.permissions;return membershipText('membership.rolePermissions','Selected role permissions: ')+(Array.isArray(permissions)?permissions.length?(globalThis.LcTenantInvitations?.permissionSummary?globalThis.LcTenantInvitations.permissionSummary(permissions):permissions.slice().sort().map(membershipPermissionLabel).join(' · ')):membershipText('membership.noPermissions','No additional permissions'):membershipText('membership.permissionsUnavailable','Unavailable — reload before assigning.'));}
/* End membership overview */
