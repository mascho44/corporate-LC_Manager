function inboxNewCaseHtml(item){
 if(!can('LC_EDIT')||!can('DOCUMENT_UPLOAD'))return '';
 return `<details class="inbox-new-case"><summary>+ Neue LC-Akte aus Avisierungsschreiben</summary><p>Neue Akte erstellen und dieses Dokument direkt darin speichern. LC-Daten fachlich prüfen; ein Rechnungsbetrag ist nicht automatisch der LC-Betrag.</p><form data-inbox-new-case="${item.id}" class="form-grid"><label>Bankprofil für gelernte Zuordnungen<input name="advisingBankProfile" maxlength="100" placeholder="BIC oder Bankname wie im Training"></label><button type="button" data-advising-preview>Angaben aus Avisierungsschreiben lesen</button><div data-advising-evidence></div><label>LC-Referenz *<input name="reference" required maxlength="255" value="${esc(item.extractedReference||'')}"></label><label>Referenz eigene Bank<input name="ownBankReference" maxlength="255"></label><label>Referenz Fremdbank<input name="foreignBankReference" maxlength="255"></label><label>Antragsteller<input name="applicant" maxlength="255"></label><label>Begünstigter<input name="beneficiary" maxlength="255"></label><label>LC-Betrag<input name="amount" type="number" min="0" step="0.01"></label><label>Währung<input name="currency" maxlength="3" pattern="[A-Z]{3}" placeholder="EUR"></label><label>Eröffnende Bank<input name="issuingBank" maxlength="255"></label><label>Verfallsort<input name="expiryPlace" maxlength="255"></label><label>Verfallsdatum<input name="expiryDate" type="date"></label><label>Dokumenttyp *<select name="documentType" required><option value="">Bitte auswählen</option>${inboxDocumentTypes.map(([value,label])=>`<option value="${value}">${label}</option>`).join('')}</select></label><label>Kennzeichnung<select name="copyNumber">${documentCopyOptions(item.copyNumber)}</select></label><label>Dokumentdatum<input name="documentDate" type="date" value="${esc(item.extractedDocumentDate||'')}"></label><label><input name="reviewConfirmed" type="checkbox" required> LC-Daten am Original geprüft und bestätigt</label><button type="submit">Akte anlegen & Dokument übernehmen</button><p class="error" role="alert"></p></form><small>Weitere LC-Bedingungen anschließend in der Akte ergänzen. Keine automatische SWIFT-Übernahme.</small></details>`;
}
document.addEventListener('submit',async event=>{
 const form=event.target;if(!form.matches('[data-inbox-new-case]'))return;event.preventDefault();
 const values=Object.fromEntries(new FormData(form));delete values.reviewConfirmed;delete values.advisingBankProfile;values.reference=values.reference.trim();
 if(!values.reference){form.querySelector('.error').textContent='Bitte eine LC-Referenz angeben.';return;}
 if(!confirm(`Neue LC-Akte „${values.reference}“ anlegen und Dokument übernehmen?`))return;
 const button=form.querySelector('button[type=submit]');button.disabled=true;form.querySelector('.error').textContent='';
 try{
  values.copyNumber=readDocumentCopy(values.copyNumber);
  ['ownBankReference','foreignBankReference','applicant','beneficiary','amount','currency','expiryDate','documentDate','issuingBank','expiryPlace'].forEach(key=>{if(!values[key])values[key]=null;});
  const result=await json(`/api/inbox/${form.dataset.inboxNewCase}/new-case`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(values)});
  $('#inboxMessage').textContent=`LC-Akte „${values.reference}“ angelegt und Dokument gespeichert.`;
  await load();await loadInbox();
 }catch(error){form.querySelector('.error').textContent=error.message;button.disabled=false;}
});
document.addEventListener('click',async event=>{
 const button=event.target.closest('[data-advising-preview]');if(!button)return;
 const form=button.closest('form'),area=form.querySelector('[data-advising-evidence]');
 if(!confirm('Erkannte Angaben vorbelegen? Bereits ausgefüllte Felder bleiben erhalten.'))return;
 button.disabled=true;area.textContent='Avisierungsschreiben wird gelesen …';
 try{
  const proposal=await json(`/api/inbox/${form.dataset.inboxNewCase}/advising-preview?bank=${encodeURIComponent(form.elements.advisingBankProfile.value)}`);
  Object.entries(proposal.fields).forEach(([key,value])=>{const field=form.elements[key];if(field&&!field.value)field.value=value;});
  form.elements.documentType.value='ADVISING_LETTER';form.elements.reviewConfirmed.checked=false;
  area.innerHTML=`<p>Unbestätigte Vorschläge – bitte mit dem Original vergleichen.</p><dl>${Object.entries(proposal.evidence).map(([key,value])=>`<dt>${esc({reference:'Aktenreferenz',ownBankReference:'Referenz eigene Bank',foreignBankReference:'Referenz Fremdbank',applicant:'Antragsteller',beneficiary:'Begünstigter',amount:'Betrag',expiryDate:'Verfallsdatum',issuingBank:'Eröffnende Bank',expiryPlace:'Verfallsort'}[key]||key)}</dt><dd>${esc(value)}</dd>`).join('')}</dl>${proposal.warnings.map(warning=>`<p>${esc(warning)}</p>`).join('')}`;
 }catch(error){area.textContent=error.message;}finally{button.disabled=false;}
});
