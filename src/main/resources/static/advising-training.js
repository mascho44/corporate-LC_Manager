let advisingTrainingItem=null;
const advisingTargets={reference:tx('at2l.4c550b','Aktenreferenz'),ownBankReference:tx('at2l.cf5c95','Referenz eigene Bank'),foreignBankReference:tx('at2l.5423bb','Referenz Fremdbank'),applicant:tx('at2l.ccaa52','Antragsteller'),beneficiary:tx('at2l.391339','Begünstigter'),amount:tx('at2l.23e3f5','LC-Betrag mit Währung'),expiryDate:tx('at2l.f05f70','Verfallsdatum / Ort'),issuingBank:tx('at2l.56781e','Eröffnende Bank')};
const trainingDialog=document.querySelector('#trainingDialog');
const swiftTrainingBody=trainingDialog.querySelector('.training-body');
const trainingHeading=trainingDialog.querySelector('.dialoghead h2'),trainingDescription=trainingDialog.querySelector('.dialoghead p');
const swiftHeadingText=trainingHeading.textContent,swiftDescriptionText=trainingDescription.textContent;
const advisingBody=document.createElement('div');advisingBody.id='advisingTrainingBody';advisingBody.className='training-body hidden';
advisingBody.innerHTML='<form id="advisingTrainingUpload" class="form-grid"><label>'+tx('at2h.d332d0','Bankprofil (z. B. BIC)')+'<input name="bank" required maxlength="100" pattern="[^|]+"></label><label>'+tx('at2h.760be3','Avisierungsschreiben')+'<input name="file" type="file" accept=".pdf,application/pdf" required></label><button type="submit">'+tx('at2h.ffa3bb','PDF laden & erkennen')+'</button></form><p>'+tx('at2h.224f6f','Unsere/Ihre Referenz bitte der richtigen Bank zuordnen. Dieselbe Bankbezeichnung im Posteingang verwenden.')+'</p><div id="advisingTrainingStatus"></div><p id="advisingTrainingMessage" role="status"></p><div id="advisingTrainingRows" class="training-fields"></div>';
trainingDialog.querySelector('.actions').before(advisingBody);
const finishAdvice=document.createElement('button');finishAdvice.id='finishAdvisingTraining';finishAdvice.type='button';finishAdvice.className='hidden';finishAdvice.textContent=tx('at2l.c00679','Nur Training abschließen');trainingDialog.querySelector('.actions').append(finishAdvice);finishAdvice.onclick=()=>saveAdvisingTraining(true);
document.querySelector('#openTrainingPage').before(Object.assign(document.createElement('label'),{className:'training-profile-choice',innerHTML:'Erkennungsprofil<select id="trainingProfileSelect"><option value="SWIFT">SWIFT · MT700 / MT707 / MT760</option><option value="ADVISING_LETTER">'+tx('at2h.760be3','Avisierungsschreiben')+'</option></select>'}));
function setTrainingProfile(profile){
 const advice=profile==='ADVISING_LETTER';swiftTrainingBody.classList.toggle('hidden',advice);advisingBody.classList.toggle('hidden',!advice);
 trainingHeading.textContent=advice?tx('at2l.10cd40','Avisierungsschreiben · Training & Dokumentenerkennung'):swiftHeadingText;
 trainingDescription.textContent=advice?tx('at2l.93754a','Angaben am PDF prüfen, zuordnen und fachlich bestätigen. Lernwissen gilt gemeinsam, getrennt nach Bankprofil.'):swiftDescriptionText;
 document.querySelector('#confirmTraining').classList.toggle('hidden',advice);
 document.querySelector('#finishTrainingOnly')?.classList.toggle('hidden',advice);
 finishAdvice.classList.toggle('hidden',!advice||!advisingTrainingItem||advisingTrainingItem.editable===false);
 document.querySelector('#trainingProfileSelect').value=profile;
}
const openSwiftTraining=openTraining;
openTraining=function(){
 if(document.querySelector('#trainingProfileSelect').value!=='ADVISING_LETTER'){setTrainingProfile('SWIFT');return openSwiftTraining();}
 setTrainingProfile('ADVISING_LETTER');if(!trainingDialog.open)trainingDialog.showModal();
};
document.querySelector('#trainingBtn').onclick=()=>openTraining();document.querySelector('#openTrainingPage').onclick=()=>openTraining();
function renderAdvisingTraining(){
 const item=advisingTrainingItem,editable=item.editable!==false,confirmed=item.fields.filter(field=>field.review).length;
 document.querySelector('#advisingTrainingUpload').classList.add('hidden');
 document.querySelector('#advisingTrainingStatus').innerHTML=`<div class="training-summary"><b>${tx('at2h.760be3','Avisierungsschreiben')}</b><span>${item.fields.length} Angaben</span><strong>${confirmed} von ${item.fields.length} fachlich bestätigt</strong><a href="/api/training/${item.id}/document" target="_blank" rel="noopener">${tx('at2h.3ca488','Original-PDF')}</a><a href="/api/training/${item.id}/export.json">JSON</a><a href="/api/training/${item.id}/export.xml">XML</a><button type="button" id="advisingNewPdf" class="secondary">${tx('at2h.037c4d','Anderes PDF laden')}</button></div><small>${tx('at2h.6a84bb','Bestätigungen werden sofort gespeichert. Der Trainingsabschluss legt keine LC-Akte an.')}</small>`;
 document.querySelector('#advisingTrainingRows').innerHTML=item.fields.map((field,index)=>`<div class="training-field ${field.review?'review-'+field.review:''}" data-advising-index="${index}"><div class="training-field-head"><b>${esc(field.sourceLabel)}</b><label class="field-reassignment">${tx('at2h.b7af26','Zielfeld zuordnen')}<select ${editable?'':'disabled'}><option value="">${tx('at2h.98b7ce','Bitte zuordnen')}</option>${Object.entries(advisingTargets).map(([key,label])=>`<option value="${key}" ${field.code===key?'selected':''}>${label}</option>`).join('')}</select></label></div><div class="pdf-context"><small>${tx('at2h.53d182','Original im PDF')}</small><div class="snippet-frame loaded"><img data-advising-zoom src="/api/training/${item.id}/snippet/${index}" alt="PDF-Ausschnitt für ${esc(field.sourceLabel)}"><button type="button" class="snippet-zoom">${tx('at2h.4d473b','⌕ Vergrößern')}</button></div></div><div class="training-value"><small>${tx('at2h.c2a0a0','Erkannter Feldinhalt')}</small><textarea maxlength="4000" ${editable?'':'disabled'}>${esc(field.value)}</textarea>${editable?'<div class="review-actions"><button type="button" class="secondary" data-advice-review="correct">'+tx('at2h.479cbb','✓ Richtig erkannt')+'</button><button type="button" class="secondary" data-advice-review="corrected">'+tx('at2h.59d00c','✎ Korrektur bestätigen')+'</button><button type="button" class="reject-review" data-advice-review="invalid">'+tx('at2h.168033','× Nicht verwendbar')+'</button></div>':''}</div><div class="training-target"><small>${tx('at2h.099992','Zuordnung im LC-Manager')}</small><b data-advising-target>${esc(advisingTargets[field.code]||tx('at2l.d0881f','Noch nicht zugeordnet'))}</b><p data-field-review>${esc(field.review?({correct:tx('at2l.479cbb','✓ Richtig erkannt'),corrected:tx('at3.0','✓ Korrektur bestätigt'),reassigned:'✓ Neu zugeordnet',invalid:tx('at2l.168033','× Nicht verwendbar')}[field.review]||field.review):tx('at3.1','Noch nicht bestätigt'))}</p></div></div>`).join('');
 document.querySelector('#advisingNewPdf').onclick=()=>document.querySelector('#advisingTrainingUpload').classList.remove('hidden');
 document.querySelectorAll('[data-advising-index]').forEach(row=>{
  const field=item.fields[Number(row.dataset.advisingIndex)];
  const changed=()=>{field.code=row.querySelector('select').value;field.value=row.querySelector('textarea').value;field.review=null;row.querySelector('[data-field-review]').textContent=tx('at2l.6ce7d1','Geändert – erneut bestätigen');row.querySelector('[data-advising-target]').textContent=advisingTargets[field.code]||tx('at2l.d0881f','Noch nicht zugeordnet');row.className='training-field';finishAdvice.disabled=true;};
  row.querySelector('select').onchange=changed;row.querySelector('textarea').oninput=changed;
  const image=row.querySelector('[data-advising-zoom]');image.onclick=()=>openSnippetZoom(image);row.querySelector('.snippet-zoom').onclick=()=>openSnippetZoom(image);
  image.onerror=()=>{const frame=image.closest('.snippet-frame');frame.classList.add('failed');if(!frame.querySelector('span'))frame.insertAdjacentHTML('afterbegin','<span>'+tx('at2h.5dd749','Ausschnitt nicht verfügbar – Original-PDF öffnen.')+'</span>');};
  row.querySelectorAll('[data-advice-review]').forEach(button=>button.onclick=()=>saveAdvisingTraining(false,Number(row.dataset.advisingIndex),button.dataset.adviceReview));
 });
 finishAdvice.classList.toggle('hidden',!editable);finishAdvice.disabled=!item.fields.length||confirmed!==item.fields.length;
}
async function saveAdvisingTraining(finish,index,review){
 const item=advisingTrainingItem,oldReview=index==null?null:item.fields[index].review;
 if(index!=null)item.fields[index].review=review;
 document.querySelectorAll('#advisingTrainingRows button,#advisingTrainingRows select,#advisingTrainingRows textarea,#advisingTrainingUpload button').forEach(control=>control.disabled=true);finishAdvice.disabled=true;
 try{const saved=await json(`/api/training/advising/${item.id}?finish=${finish}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(item.fields)});item.fields=saved.fields;if(finish)item.editable=false;document.querySelector('#advisingTrainingMessage').textContent=finish?tx('at2l.4ad05a','Training abgeschlossen. Keine Akte angelegt.'):tx('at2l.601d49','Bestätigung gespeichert; bankbezogene Zuordnung fließt bereits ins Lernen ein.');await loadDashboardDrafts();}
 catch(error){if(index!=null)item.fields[index].review=oldReview;document.querySelector('#advisingTrainingMessage').textContent=error.message;}
 finally{document.querySelector('#advisingTrainingUpload button').disabled=false;renderAdvisingTraining();}
}
document.querySelector('#advisingTrainingUpload').onsubmit=async event=>{
 event.preventDefault();const form=event.target,button=form.querySelector('button');button.disabled=true;document.querySelector('#advisingTrainingMessage').textContent=tx('at2l.14397a','PDF wird gelesen …');
 try{advisingTrainingItem=await json('/api/training/advising',{method:'POST',body:new FormData(form)});renderAdvisingTraining();document.querySelector('#advisingTrainingMessage').textContent=tx('at2l.e2f64c','Bitte alle Angaben zuordnen und am Original prüfen.');await loadDashboardDrafts();}catch(error){document.querySelector('#advisingTrainingMessage').textContent=error.message;}finally{button.disabled=false;}
};
const openSwiftTrainingSession=openTrainingSession;
openTrainingSession=async function(id){
 const item=await json(`/api/training/${id}`);if(item.messageType!=='ADVISING_LETTER'){setTrainingProfile('SWIFT');return openSwiftTrainingSession(id);}
 advisingTrainingItem={id:item.id,fields:item.fields,username:item.username,status:item.status,lcId:item.lcId,editable:item.status==='DRAFT'&&item.username===currentUser.username};setTrainingProfile('ADVISING_LETTER');renderAdvisingTraining();document.querySelector('#advisingTrainingMessage').textContent=item.filename;if(!trainingDialog.open)trainingDialog.showModal();
};
