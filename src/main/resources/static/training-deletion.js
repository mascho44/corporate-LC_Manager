// Use the existing soft-delete API. Learning records and linked LC cases are retained.
function mayDeleteTraining(item){return can('TRAINING_MANAGE')&&(currentUser.role==='ADMIN'||item.username===currentUser.username);}
const adviceHistory=document.createElement('div');adviceHistory.id='advisingTrainingHistory';adviceHistory.className='training-history';advisingBody.prepend(adviceHistory);
const deleteAdviceButton=document.createElement('button');deleteAdviceButton.type='button';deleteAdviceButton.className='danger hidden';deleteAdviceButton.textContent='Trainingsvorlage löschen';finishAdvice.before(deleteAdviceButton);
let adviceTrainingSaving=false;
function updateAdviceDeleteButton(){
 const item=advisingTrainingItem;
 deleteAdviceButton.classList.toggle('hidden',!item||document.querySelector('#trainingProfileSelect').value!=='ADVISING_LETTER'||!mayDeleteTraining({...item,username:item.username||currentUser.username}));
 deleteAdviceButton.disabled=adviceTrainingSaving||(typeof advisingCaseBusy!=='undefined'&&advisingCaseBusy);
}
async function removeTrainingTemplates(ids,label){
 if(adviceTrainingSaving||(typeof advisingCaseBusy!=='undefined'&&advisingCaseBusy)){alert('Bitte warten, bis das Speichern abgeschlossen ist.');return;}
 if(!ids.length){alert('Bitte zuerst Trainingsvorlagen auswählen.');return;}
 if(!confirm(`${label} ausblenden? Bereits gespeicherte Lerninhalte und LC-Akten bleiben erhalten.`))return;
 const controls=[deleteAdviceButton,...adviceHistory.querySelectorAll('button')];controls.forEach(button=>button.disabled=true);
 try{
  await json('/api/training/templates',{method:'DELETE',headers:{'Content-Type':'application/json'},body:JSON.stringify(ids)});
  if(advisingTrainingItem&&ids.includes(advisingTrainingItem.id)){
   advisingTrainingItem=null;document.querySelector('#advisingTrainingRows').replaceChildren();document.querySelector('#advisingTrainingStatus').replaceChildren();
   document.querySelector('#advisingTrainingUpload').reset();document.querySelector('#advisingTrainingUpload').classList.remove('hidden');
   document.querySelector('#advisingCaseArea')?.classList.add('hidden');finishAdvice.classList.add('hidden');setTrainingProfile('ADVISING_LETTER');
  }
  document.querySelector('#advisingTrainingMessage').textContent='Trainingsvorlage ausgeblendet. Lerninhalte bleiben erhalten.';
  await Promise.all([loadAdviceDeletionHistory(),loadDashboardDrafts()]);
  if(document.querySelector('#trainingHistory'))await loadTrainingHistory();
 }catch(error){
  document.querySelector('#advisingTrainingMessage').textContent='Löschen fehlgeschlagen: '+error.message;
  if(!trainingDialog.open)alert('Löschen fehlgeschlagen: '+error.message);
 }finally{controls.forEach(button=>button.disabled=false);updateAdviceDeleteButton();}
}
deleteAdviceButton.onclick=()=>removeTrainingTemplates([advisingTrainingItem.id],'Diese Trainingsvorlage');
async function loadAdviceDeletionHistory(){
 try{
  const rows=(await json('/api/training')).filter(item=>item.messageType==='ADVISING_LETTER');
  adviceHistory.innerHTML=rows.length?`<details open><summary>Avisierungs-Trainingshistorie (${rows.length})</summary><div class="training-bulk-actions">${rows.some(mayDeleteTraining)?'<button type="button" class="danger" data-advice-delete-selected>Ausgewählte Vorlagen löschen</button>':''}<small>Die Lerninhalte bleiben erhalten.</small></div>${rows.map(item=>`<div class="training-history-row selectable">${mayDeleteTraining(item)?`<input type="checkbox" data-advice-delete-choice value="${item.id}" aria-label="${esc(item.filename)} auswählen">`:'<span></span>'}<div><b>${esc(item.filename)}</b><small>${esc(item.username)} · ${item.status==='DRAFT'?'Entwurf':'Bestätigt'}</small></div><div class="training-links"><button type="button" class="secondary" data-advice-open="${item.id}">Öffnen</button>${mayDeleteTraining(item)?`<button type="button" class="danger" data-advice-delete="${item.id}">Löschen</button>`:''}</div></div>`).join('')}</details>`:'<small>Noch keine Avisierungs-Trainingsvorlagen vorhanden.</small>';
  adviceHistory.querySelectorAll('[data-advice-open]').forEach(button=>button.onclick=()=>openTrainingSession(button.dataset.adviceOpen));
  adviceHistory.querySelectorAll('[data-advice-delete]').forEach(button=>button.onclick=()=>removeTrainingTemplates([button.dataset.adviceDelete],'Diese Trainingsvorlage'));
  adviceHistory.querySelector('[data-advice-delete-selected]')?.addEventListener('click',()=>removeTrainingTemplates([...adviceHistory.querySelectorAll('[data-advice-delete-choice]:checked')].map(input=>input.value),'Die ausgewählten Trainingsvorlagen'));
 }catch(error){adviceHistory.textContent='Trainingshistorie konnte nicht geladen werden: '+error.message;}
}
const openTrainingWithDeletion=openTraining;
openTraining=function(){const result=openTrainingWithDeletion();updateAdviceDeleteButton();if(document.querySelector('#trainingProfileSelect').value==='ADVISING_LETTER')loadAdviceDeletionHistory();return result;};
const renderAdviceWithDeletion=renderAdvisingTraining;
renderAdvisingTraining=function(){renderAdviceWithDeletion();updateAdviceDeleteButton();loadAdviceDeletionHistory();};
const saveAdviceWithDeletion=saveAdvisingTraining;
saveAdvisingTraining=async function(...args){adviceTrainingSaving=true;updateAdviceDeleteButton();try{return await saveAdviceWithDeletion(...args);}finally{adviceTrainingSaving=false;updateAdviceDeleteButton();}};
const setProfileWithDeletion=setTrainingProfile;
setTrainingProfile=function(profile){setProfileWithDeletion(profile);updateAdviceDeleteButton();};
const loadDraftsWithDeletion=loadDashboardDrafts;
loadDashboardDrafts=async function(){
 await loadDraftsWithDeletion();
 const rows=await json('/api/training').catch(()=>[]);
 document.querySelectorAll('#trainingDraftList [data-training-id]').forEach(open=>{
  const item=rows.find(row=>row.id===open.dataset.trainingId);if(!item||!mayDeleteTraining(item))return;
  const button=document.createElement('button');button.type='button';button.className='danger';button.textContent='Löschen';
  button.onclick=()=>removeTrainingTemplates([item.id],'Diese Trainingsvorlage');
  const actions=document.createElement('div');actions.className='training-links';open.before(actions);actions.append(open,button);
 });
};
