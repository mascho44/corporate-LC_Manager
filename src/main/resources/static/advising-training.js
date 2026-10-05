let advisingTrainingItem=null;
const advisingTargets={reference:'Aktenreferenz',ownBankReference:'Referenz eigene Bank',foreignBankReference:'Referenz Fremdbank',applicant:'Antragsteller',beneficiary:'Begünstigter',amount:'LC-Betrag mit Währung',expiryDate:'Verfallsdatum'};
const advisingSection=document.createElement('section');advisingSection.id='advisingTrainingSection';advisingSection.className='panel';
advisingSection.innerHTML='<h2>Avisierungsschreiben trainieren</h2><p>Beschriftete Angaben zuordnen und am PDF prüfen. Lernen speichert Feldzuordnungen, nicht die Geschäftswerte. Dieselbe Bankbezeichnung im Posteingang verwenden.</p><form id="advisingTrainingUpload" class="form-grid"><label>Bankprofil (z. B. BIC)<input name="bank" required maxlength="100" pattern="[^|]+"></label><label>Avisierungsschreiben<input name="file" type="file" accept=".pdf,application/pdf" required></label><button type="submit">Training starten</button></form><p id="advisingTrainingMessage" role="status"></p><div id="advisingTrainingRows"></div>';
document.querySelector('#trainingSection').append(advisingSection);
function renderAdvisingTraining(){
 const item=advisingTrainingItem,editable=item.editable!==false;
 document.querySelector('#advisingTrainingRows').innerHTML=`<p><a class="button-link" href="/api/training/${item.id}/document" target="_blank" rel="noopener">Original-PDF öffnen</a> · <a href="/api/training/${item.id}/export.json">JSON</a> · <a href="/api/training/${item.id}/export.xml">XML</a></p>${item.fields.map((field,index)=>`<article class="card advising-training-row" data-advising-index="${index}"><h3>${esc(field.sourceLabel)}</h3><div class="evidence-workspace"><section><img style="max-width:100%" src="/api/training/${item.id}/snippet/${index}" alt="PDF-Ausschnitt für ${esc(field.sourceLabel)}"></section><section><label>Zielfeld<select ${editable?'':'disabled'}>${'<option value="">Bitte zuordnen</option>'+Object.entries(advisingTargets).map(([key,label])=>`<option value="${key}" ${field.code===key?'selected':''}>${label}</option>`).join('')}</select></label><label>Erkannter Wert<textarea maxlength="4000" ${editable?'':'disabled'}>${esc(field.value)}</textarea></label><p data-field-review>${esc(field.review?'Gespeichert: '+({correct:'Richtig erkannt',corrected:'Korrigiert',reassigned:'Neu zugeordnet',invalid:'Nicht verwendbar'}[field.review]||field.review):'Noch nicht bestätigt')}</p>${editable?'<button type="button" data-advice-review="correct">Richtig erkannt</button> <button type="button" data-advice-review="corrected">Korrektur / Zuordnung bestätigen</button> <button type="button" class="danger" data-advice-review="invalid">Nicht verwendbar</button>':''}</section></div></article>`).join('')}${editable?'<button type="button" id="finishAdvisingTraining">Training abschließen – keine Akte anlegen</button>':''}`;
 document.querySelectorAll('[data-advising-index]').forEach(row=>{
  const field=item.fields[Number(row.dataset.advisingIndex)];
  const changed=()=>{field.code=row.querySelector('select').value;field.value=row.querySelector('textarea').value;field.review=null;row.querySelector('[data-field-review]').textContent='Geändert – erneut bestätigen';};
  row.querySelector('select').onchange=changed;row.querySelector('textarea').oninput=changed;
  row.querySelectorAll('[data-advice-review]').forEach(button=>button.onclick=()=>saveAdvisingTraining(false,Number(row.dataset.advisingIndex),button.dataset.adviceReview));
 });
 document.querySelector('#finishAdvisingTraining')?.addEventListener('click',()=>saveAdvisingTraining(true));
}
async function saveAdvisingTraining(finish,index,review){
 const item=advisingTrainingItem,oldReview=index==null?null:item.fields[index].review;
 if(index!=null)item.fields[index].review=review;
 document.querySelectorAll('#advisingTrainingRows button,#advisingTrainingRows select,#advisingTrainingRows textarea').forEach(control=>control.disabled=true);
 try{const saved=await json(`/api/training/advising/${item.id}?finish=${finish}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(item.fields)});item.fields=saved.fields;if(finish)item.editable=false;document.querySelector('#advisingTrainingMessage').textContent=finish?'Training abgeschlossen. Keine Akte angelegt.':'Bestätigung gespeichert. Bankbezogene Zuordnung fließt bereits ins Lernen ein.';}
 catch(error){if(index!=null)item.fields[index].review=oldReview;document.querySelector('#advisingTrainingMessage').textContent=error.message;}
 renderAdvisingTraining();
}
document.querySelector('#advisingTrainingUpload').onsubmit=async event=>{
 event.preventDefault();const form=event.target,button=form.querySelector('button');button.disabled=true;
 try{advisingTrainingItem=await json('/api/training/advising',{method:'POST',body:new FormData(form)});renderAdvisingTraining();document.querySelector('#advisingTrainingMessage').textContent='Bitte alle Angaben zuordnen und am Original prüfen.';}catch(error){document.querySelector('#advisingTrainingMessage').textContent=error.message;}finally{button.disabled=false;}
};
const openSwiftTrainingSession=openTrainingSession;
openTrainingSession=async function(id){const item=await json(`/api/training/${id}`);if(item.messageType!=='ADVISING_LETTER')return openSwiftTrainingSession(id);document.querySelector('#trainingDialog')?.close();advisingTrainingItem={id:item.id,fields:item.fields,editable:item.status==='DRAFT'&&item.username===currentUser.username};renderAdvisingTraining();advisingSection.scrollIntoView({behavior:'smooth'});};
