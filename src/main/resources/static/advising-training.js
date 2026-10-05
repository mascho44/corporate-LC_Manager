let advisingTrainingItem=null;
const advisingTargets={reference:'Aktenreferenz',ownBankReference:'Referenz eigene Bank',foreignBankReference:'Referenz Fremdbank',applicant:'Antragsteller',beneficiary:'Begünstigter',amount:'LC-Betrag mit Währung',expiryDate:'Verfallsdatum / Ort',issuingBank:'Eröffnende Bank'};
const trainingDialog=document.querySelector('#trainingDialog');
const swiftTrainingBody=trainingDialog.querySelector('.training-body');
const trainingHeading=trainingDialog.querySelector('.dialoghead h2'),trainingDescription=trainingDialog.querySelector('.dialoghead p');
const swiftHeadingText=trainingHeading.textContent,swiftDescriptionText=trainingDescription.textContent;
const advisingBody=document.createElement('div');advisingBody.id='advisingTrainingBody';advisingBody.className='training-body hidden';
advisingBody.innerHTML='<form id="advisingTrainingUpload" class="form-grid"><label>Bankprofil (z. B. BIC)<input name="bank" required maxlength="100" pattern="[^|]+"></label><label>Avisierungsschreiben<input name="file" type="file" accept=".pdf,application/pdf" required></label><button type="submit">PDF laden & erkennen</button></form><p>Unsere/Ihre Referenz bitte der richtigen Bank zuordnen. Dieselbe Bankbezeichnung im Posteingang verwenden.</p><div id="advisingTrainingStatus"></div><p id="advisingTrainingMessage" role="status"></p><div id="advisingTrainingRows" class="training-fields"></div>';
trainingDialog.querySelector('.actions').before(advisingBody);
const finishAdvice=document.createElement('button');finishAdvice.id='finishAdvisingTraining';finishAdvice.type='button';finishAdvice.className='hidden';finishAdvice.textContent='Nur Training abschließen';trainingDialog.querySelector('.actions').append(finishAdvice);finishAdvice.onclick=()=>saveAdvisingTraining(true);
document.querySelector('#openTrainingPage').before(Object.assign(document.createElement('label'),{className:'training-profile-choice',innerHTML:'Erkennungsprofil<select id="trainingProfileSelect"><option value="SWIFT">SWIFT · MT700 / MT707 / MT760</option><option value="ADVISING_LETTER">Avisierungsschreiben</option></select>'}));
function setTrainingProfile(profile){
 const advice=profile==='ADVISING_LETTER';swiftTrainingBody.classList.toggle('hidden',advice);advisingBody.classList.toggle('hidden',!advice);
 trainingHeading.textContent=advice?'Avisierungsschreiben · Training & Dokumentenerkennung':swiftHeadingText;
 trainingDescription.textContent=advice?'Angaben am PDF prüfen, zuordnen und fachlich bestätigen. Lernwissen gilt gemeinsam, getrennt nach Bankprofil.':swiftDescriptionText;
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
 document.querySelector('#advisingTrainingStatus').innerHTML=`<div class="training-summary"><b>Avisierungsschreiben</b><span>${item.fields.length} Angaben</span><strong>${confirmed} von ${item.fields.length} fachlich bestätigt</strong><a href="/api/training/${item.id}/document" target="_blank" rel="noopener">Original-PDF</a><a href="/api/training/${item.id}/export.json">JSON</a><a href="/api/training/${item.id}/export.xml">XML</a><button type="button" id="advisingNewPdf" class="secondary">Anderes PDF laden</button></div><small>Bestätigungen werden sofort gespeichert. Der Trainingsabschluss legt keine LC-Akte an.</small>`;
 document.querySelector('#advisingTrainingRows').innerHTML=item.fields.map((field,index)=>`<div class="training-field ${field.review?'review-'+field.review:''}" data-advising-index="${index}"><div class="training-field-head"><b>${esc(field.sourceLabel)}</b><label class="field-reassignment">Zielfeld zuordnen<select ${editable?'':'disabled'}><option value="">Bitte zuordnen</option>${Object.entries(advisingTargets).map(([key,label])=>`<option value="${key}" ${field.code===key?'selected':''}>${label}</option>`).join('')}</select></label></div><div class="pdf-context"><small>Original im PDF</small><div class="snippet-frame loaded"><img data-advising-zoom src="/api/training/${item.id}/snippet/${index}" alt="PDF-Ausschnitt für ${esc(field.sourceLabel)}"><button type="button" class="snippet-zoom">⌕ Vergrößern</button></div></div><div class="training-value"><small>Erkannter Feldinhalt</small><textarea maxlength="4000" ${editable?'':'disabled'}>${esc(field.value)}</textarea>${editable?'<div class="review-actions"><button type="button" class="secondary" data-advice-review="correct">✓ Richtig erkannt</button><button type="button" class="secondary" data-advice-review="corrected">✎ Korrektur bestätigen</button><button type="button" class="reject-review" data-advice-review="invalid">× Nicht verwendbar</button></div>':''}</div><div class="training-target"><small>Zuordnung im LC-Manager</small><b data-advising-target>${esc(advisingTargets[field.code]||'Noch nicht zugeordnet')}</b><p data-field-review>${esc(field.review?({correct:'✓ Richtig erkannt',corrected:'✓ Korrektur bestätigt',reassigned:'✓ Neu zugeordnet',invalid:'× Nicht verwendbar'}[field.review]||field.review):'Noch nicht bestätigt')}</p></div></div>`).join('');
 document.querySelector('#advisingNewPdf').onclick=()=>document.querySelector('#advisingTrainingUpload').classList.remove('hidden');
 document.querySelectorAll('[data-advising-index]').forEach(row=>{
  const field=item.fields[Number(row.dataset.advisingIndex)];
  const changed=()=>{field.code=row.querySelector('select').value;field.value=row.querySelector('textarea').value;field.review=null;row.querySelector('[data-field-review]').textContent='Geändert – erneut bestätigen';row.querySelector('[data-advising-target]').textContent=advisingTargets[field.code]||'Noch nicht zugeordnet';row.className='training-field';finishAdvice.disabled=true;};
  row.querySelector('select').onchange=changed;row.querySelector('textarea').oninput=changed;
  const image=row.querySelector('[data-advising-zoom]');image.onclick=()=>openSnippetZoom(image);row.querySelector('.snippet-zoom').onclick=()=>openSnippetZoom(image);
  image.onerror=()=>{const frame=image.closest('.snippet-frame');frame.classList.add('failed');if(!frame.querySelector('span'))frame.insertAdjacentHTML('afterbegin','<span>Ausschnitt nicht verfügbar – Original-PDF öffnen.</span>');};
  row.querySelectorAll('[data-advice-review]').forEach(button=>button.onclick=()=>saveAdvisingTraining(false,Number(row.dataset.advisingIndex),button.dataset.adviceReview));
 });
 finishAdvice.classList.toggle('hidden',!editable);finishAdvice.disabled=!item.fields.length||confirmed!==item.fields.length;
}
async function saveAdvisingTraining(finish,index,review){
 const item=advisingTrainingItem,oldReview=index==null?null:item.fields[index].review;
 if(index!=null)item.fields[index].review=review;
 document.querySelectorAll('#advisingTrainingRows button,#advisingTrainingRows select,#advisingTrainingRows textarea,#advisingTrainingUpload button').forEach(control=>control.disabled=true);finishAdvice.disabled=true;
 try{const saved=await json(`/api/training/advising/${item.id}?finish=${finish}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(item.fields)});item.fields=saved.fields;if(finish)item.editable=false;document.querySelector('#advisingTrainingMessage').textContent=finish?'Training abgeschlossen. Keine Akte angelegt.':'Bestätigung gespeichert; bankbezogene Zuordnung fließt bereits ins Lernen ein.';await loadDashboardDrafts();}
 catch(error){if(index!=null)item.fields[index].review=oldReview;document.querySelector('#advisingTrainingMessage').textContent=error.message;}
 finally{document.querySelector('#advisingTrainingUpload button').disabled=false;renderAdvisingTraining();}
}
document.querySelector('#advisingTrainingUpload').onsubmit=async event=>{
 event.preventDefault();const form=event.target,button=form.querySelector('button');button.disabled=true;document.querySelector('#advisingTrainingMessage').textContent='PDF wird gelesen …';
 try{advisingTrainingItem=await json('/api/training/advising',{method:'POST',body:new FormData(form)});renderAdvisingTraining();document.querySelector('#advisingTrainingMessage').textContent='Bitte alle Angaben zuordnen und am Original prüfen.';await loadDashboardDrafts();}catch(error){document.querySelector('#advisingTrainingMessage').textContent=error.message;}finally{button.disabled=false;}
};
const openSwiftTrainingSession=openTrainingSession;
openTrainingSession=async function(id){
 const item=await json(`/api/training/${id}`);if(item.messageType!=='ADVISING_LETTER'){setTrainingProfile('SWIFT');return openSwiftTrainingSession(id);}
 advisingTrainingItem={id:item.id,fields:item.fields,editable:item.status==='DRAFT'&&item.username===currentUser.username};setTrainingProfile('ADVISING_LETTER');renderAdvisingTraining();document.querySelector('#advisingTrainingMessage').textContent=item.filename;if(!trainingDialog.open)trainingDialog.showModal();
};
