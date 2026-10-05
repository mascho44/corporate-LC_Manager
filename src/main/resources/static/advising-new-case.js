const advisingNewCaseButton=document.createElement('button');
advisingNewCaseButton.type='button';advisingNewCaseButton.className='hidden';advisingNewCaseButton.textContent='Bestätigte Angaben in neue LC-Akte übernehmen';
finishAdvice.after(advisingNewCaseButton);
const advisingCaseArea=document.createElement('section');advisingCaseArea.id='advisingCaseArea';advisingCaseArea.className='advising-case-area hidden';advisingBody.append(advisingCaseArea);
let advisingCaseBusy=false;
function updateAdvisingCaseButton(){
 const item=advisingTrainingItem,own=item&&(item.username||currentUser.username)===currentUser.username;
 const allowed=can('TRAINING_MANAGE')&&can('LC_EDIT')&&can('DOCUMENT_UPLOAD');
 const advice=document.querySelector('#trainingProfileSelect').value==='ADVISING_LETTER';
 advisingNewCaseButton.classList.toggle('hidden',!advice||!own||!allowed||!!item?.lcId||item?.status==='DELETED');
 advisingNewCaseButton.disabled=advisingCaseBusy||!item?.fields.length||item.fields.some(field=>!field.review);
}
const renderAdviceWithCase=renderAdvisingTraining;
renderAdvisingTraining=function(){renderAdviceWithCase();advisingCaseArea.classList.add('hidden');updateAdvisingCaseButton();if(advisingTrainingItem.lcId){advisingCaseArea.innerHTML='<button type="button" id="openLinkedAdvisingCase">Verknüpfte LC-Akte öffnen</button>';advisingCaseArea.classList.remove('hidden');document.querySelector('#openLinkedAdvisingCase').onclick=async()=>{trainingDialog.close();await show(advisingTrainingItem.lcId);};}};
const setProfileWithCase=setTrainingProfile;
setTrainingProfile=function(profile){setProfileWithCase(profile);updateAdvisingCaseButton();};
advisingBody.addEventListener('input',event=>{if(event.target.closest('[data-advising-index]')){advisingNewCaseButton.disabled=true;advisingCaseArea.classList.add('hidden');}});
advisingBody.addEventListener('change',event=>{if(event.target.closest('[data-advising-index]')){advisingNewCaseButton.disabled=true;advisingCaseArea.classList.add('hidden');}});
function advisingCaseControls(busy){
 advisingCaseBusy=busy;
 document.querySelectorAll('#advisingTrainingRows button,#advisingTrainingRows select,#advisingTrainingRows textarea,#advisingTrainingUpload button,#advisingNewPdf').forEach(control=>control.disabled=busy||advisingTrainingItem.editable===false&&control.matches('select,textarea,[data-advice-review]'));
 finishAdvice.disabled=busy||!advisingTrainingItem.fields.length||advisingTrainingItem.fields.some(field=>!field.review);
 updateAdvisingCaseButton();
}
advisingNewCaseButton.onclick=async()=>{
 const item=advisingTrainingItem;advisingCaseControls(true);
 try{
  const preview=await json(`/api/training/advising/${item.id}/new-case`);
  if(item!==advisingTrainingItem)return;
  const labels={reference:'Aktenreferenz',ownBankReference:'Referenz eigene Bank',foreignBankReference:'Referenz Fremdbank',applicant:'Antragsteller',beneficiary:'Begünstigter',amount:'LC-Betrag',currency:'Währung',expiryDate:'Verfallsdatum',issuingBank:'Eröffnende Bank',expiryPlace:'Verfallsort'};
  const required=['reference','applicant','beneficiary','amount','currency','expiryDate'];
  advisingCaseArea.innerHTML=`<h3>Neue LC-Akte anlegen</h3><p>Bestätigte Angaben sind vorbelegt. Mit * gekennzeichnete Angaben ergänzen und am Original prüfen. Das Avisierungsschreiben wird in der Akte gespeichert.</p><div class="advising-case-warnings">${preview.warnings.filter(warning=>!warning.startsWith('Vorschläge sind unbestätigt')&&!warning.startsWith('Unsere/Ihre Referenz')).map(warning=>`<p>${esc(warning)}</p>`).join('')}</div><form id="advisingCaseForm" class="form-grid">${Object.entries(labels).map(([key,label])=>`<label>${label}${required.includes(key)?' *':''}<input name="${key}" ${required.includes(key)?'required':''} ${key==='amount'?'type="number" min="0.01" step="0.01"':key==='expiryDate'?'type="date"':key==='currency'?'maxlength="3" pattern="[A-Z]{3}" placeholder="EUR"':'maxlength="255"'} value="${esc(preview.fields[key]||'')}"></label>`).join('')}<label class="wide check-label"><input type="checkbox" name="reviewConfirmed" required> Angaben geprüft – neue Akte anlegen und Original übernehmen</label><p id="advisingCaseError" class="error wide" role="alert"></p><div class="wide"><button type="submit">LC-Akte anlegen & Original speichern</button> <button type="button" id="cancelAdvisingCase" class="secondary">Zur Feldprüfung</button></div></form>`;
  advisingCaseArea.classList.remove('hidden');
  document.querySelector('#cancelAdvisingCase').onclick=()=>advisingCaseArea.classList.add('hidden');
  document.querySelector('#advisingCaseForm').onsubmit=event=>createAdvisingCase(event,item);
  advisingCaseArea.scrollIntoView({behavior:'smooth',block:'start'});
 }catch(error){document.querySelector('#advisingTrainingMessage').textContent=error.message;}
 finally{advisingCaseControls(false);}
};
async function createAdvisingCase(event,item){
 event.preventDefault();if(item!==advisingTrainingItem||advisingCaseBusy)return;
 const form=event.target,values=Object.fromEntries(new FormData(form));delete values.reviewConfirmed;
 Object.keys(values).forEach(key=>values[key]=values[key].trim()||null);
 const errorArea=document.querySelector('#advisingCaseError');errorArea.textContent='';
 advisingCaseControls(true);form.querySelectorAll('input,button').forEach(control=>control.disabled=true);
 let result;
 try{result=await json(`/api/training/advising/${item.id}/new-case`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(values)});}
 catch(error){errorArea.textContent=error.message;form.querySelectorAll('input,button').forEach(control=>control.disabled=false);}
 finally{advisingCaseControls(false);}
 if(!result)return;
 item.lcId=result.lcId;item.status='CONFIRMED';item.editable=false;renderAdvisingTraining();
 document.querySelector('#advisingTrainingMessage').textContent=`LC-Akte „${values.reference}“ angelegt; Original-PDF gespeichert und Übernahme protokolliert.`;
 advisingCaseArea.innerHTML='<button type="button" id="openCreatedAdvisingCase">Neue LC-Akte öffnen</button>';advisingCaseArea.classList.remove('hidden');
 document.querySelector('#openCreatedAdvisingCase').onclick=async()=>{trainingDialog.close();await show(result.lcId);};
 try{await load();}catch(error){document.querySelector('#advisingTrainingMessage').textContent+=' Die Übersicht konnte nicht aktualisiert werden; bitte neu laden.';}
}
