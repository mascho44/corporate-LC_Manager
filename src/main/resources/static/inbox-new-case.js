function inboxNewCaseHtml(item){
 if(!can('LC_EDIT')||!can('DOCUMENT_UPLOAD'))return '';
 return `<details class="inbox-new-case"><summary>${tx('inh.a9a78d','+ Neue LC-Akte aus Avisierungsschreiben')}</summary><p>${tx('inh.3de1f7','Neue Akte erstellen und dieses Dokument direkt darin speichern. LC-Daten fachlich prüfen; ein Rechnungsbetrag ist nicht automatisch der LC-Betrag.')}</p><form data-inbox-new-case="${item.id}" class="form-grid"><label>${tx('inh.19a9ec','Bankprofil für gelernte Zuordnungen')}<input name="advisingBankProfile" maxlength="100" placeholder="${tx('inh.19c099','BIC oder Bankname wie im Training')}"></label><button type="button" data-advising-preview>${tx('inh.bc8d62','Angaben aus Avisierungsschreiben lesen')}</button><div data-advising-evidence></div><label>${tx('inh.839bbc','LC-Referenz *')}<input name="reference" required maxlength="255" value="${esc(item.extractedReference||'')}"></label><label>${tx('inh.cf5c95','Referenz eigene Bank')}<input name="ownBankReference" maxlength="255"></label><label>${tx('inh.5423bb','Referenz Fremdbank')}<input name="foreignBankReference" maxlength="255"></label><label>${tx('inh.ccaa52','Antragsteller')}<input name="applicant" maxlength="255"></label><label>${tx('inh.391339','Begünstigter')}<input name="beneficiary" maxlength="255"></label><label>${tx('inh.370950','LC-Betrag')}<input name="amount" type="number" min="0" step="0.01"></label><label>${tx('inh.f73cd5','Währung')}<input name="currency" maxlength="3" pattern="[A-Z]{3}" placeholder="EUR"></label><label>${tx('inh.56781e','Eröffnende Bank')}<input name="issuingBank" maxlength="255"></label><label>${tx('inh.d1f01c','Verfallsort')}<input name="expiryPlace" maxlength="255"></label><label>${tx('inh.b22b24','Verfallsdatum')}<input name="expiryDate" type="date"></label><label>${tx('inh.c3bbca','Dokumenttyp *')}<select name="documentType" required><option value="">${tx('inh.f59c72','Bitte auswählen')}</option>${inboxDocumentTypes.map(([value,label])=>`<option value="${value}">${label}</option>`).join('')}</select></label><label>${tx('inh.10eff1','Kennzeichnung')}<select name="copyNumber">${documentCopyOptions(item.copyNumber)}</select></label><label>${tx('inh.8d2ca8','Dokumentdatum')}<input name="documentDate" type="date" value="${esc(item.extractedDocumentDate||'')}"></label><label><input name="reviewConfirmed" type="checkbox" required> ${tx('inh.2d6b41','LC-Daten am Original geprüft und bestätigt')}</label><button type="submit">${tx('inh.95f0a9','Akte anlegen & Dokument übernehmen')}</button><p class="error" role="alert"></p></form><small>${tx('inh.33e632','Weitere LC-Bedingungen anschließend in der Akte ergänzen. Keine automatische SWIFT-Übernahme.')}</small></details>`;
}
document.addEventListener('submit',async event=>{
 const form=event.target;if(!form.matches('[data-inbox-new-case]'))return;event.preventDefault();
 const values=Object.fromEntries(new FormData(form));delete values.reviewConfirmed;delete values.advisingBankProfile;values.reference=values.reference.trim();
 if(!values.reference){form.querySelector('.error').textContent=tx('inl.099c08','Bitte eine LC-Referenz angeben.');return;}
 if(!confirm(tx('int.ff8891','Neue LC-Akte „{p0}“ anlegen und Dokument übernehmen?',{p0:values.reference})))return;
 const button=form.querySelector('button[type=submit]');button.disabled=true;form.querySelector('.error').textContent='';
 try{
  values.copyNumber=readDocumentCopy(values.copyNumber);
  ['ownBankReference','foreignBankReference','applicant','beneficiary','amount','currency','expiryDate','documentDate','issuingBank','expiryPlace'].forEach(key=>{if(!values[key])values[key]=null;});
  const result=await json(`/api/inbox/${form.dataset.inboxNewCase}/new-case`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(values)});
  $('#inboxMessage').textContent=tx('int.ac4a2c','LC-Akte „{p0}“ angelegt und Dokument gespeichert.',{p0:values.reference});
  await load();await loadInbox();
 }catch(error){form.querySelector('.error').textContent=error.message;button.disabled=false;}
});
document.addEventListener('click',async event=>{
 const button=event.target.closest('[data-advising-preview]');if(!button)return;
 const form=button.closest('form'),area=form.querySelector('[data-advising-evidence]');
 if(!confirm(tx('inl.f24c80','Erkannte Angaben vorbelegen? Bereits ausgefüllte Felder bleiben erhalten.')))return;
 button.disabled=true;area.textContent=tx('inl.2b74b8','Avisierungsschreiben wird gelesen …');
 try{
  const proposal=await json(`/api/inbox/${form.dataset.inboxNewCase}/advising-preview?bank=${encodeURIComponent(form.elements.advisingBankProfile.value)}`);
  Object.entries(proposal.fields).forEach(([key,value])=>{const field=form.elements[key];if(field&&!field.value)field.value=value;});
  form.elements.documentType.value='ADVISING_LETTER';form.elements.reviewConfirmed.checked=false;
  area.innerHTML=`<p>${tx('inh.58e3f5','Unbestätigte Vorschläge – bitte mit dem Original vergleichen.')}</p><dl>${Object.entries(proposal.evidence).map(([key,value])=>`<dt>${esc({reference:tx('inl.4c550b','Aktenreferenz'),ownBankReference:tx('inl.cf5c95','Referenz eigene Bank'),foreignBankReference:tx('inl.5423bb','Referenz Fremdbank'),applicant:tx('inl.ccaa52','Antragsteller'),beneficiary:tx('inl.391339','Begünstigter'),amount:tx('inl.000374','Betrag'),expiryDate:tx('inl.b22b24','Verfallsdatum'),issuingBank:tx('inl.56781e','Eröffnende Bank'),expiryPlace:tx('inl.d1f01c','Verfallsort')}[key]||key)}</dt><dd>${esc(value)}</dd>`).join('')}</dl>${proposal.warnings.map(warning=>`<p>${esc(warning)}</p>`).join('')}`;
 }catch(error){area.textContent=error.message;}finally{button.disabled=false;}
});
