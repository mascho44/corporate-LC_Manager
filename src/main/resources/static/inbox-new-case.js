function inboxNewCaseHtml(item){
 if(!can('LC_EDIT')||!can('DOCUMENT_UPLOAD'))return '';
 return `<details class="inbox-new-case"><summary>+ Neue LC-Akte anlegen</summary><p>Neue Akte erstellen und dieses Dokument direkt darin speichern. LC-Daten fachlich prüfen; ein Rechnungsbetrag ist nicht automatisch der LC-Betrag.</p><form data-inbox-new-case="${item.id}" class="form-grid"><label>LC-Referenz *<input name="reference" required maxlength="255" value="${esc(item.extractedReference||'')}"></label><label>Referenz eigene Bank<input name="ownBankReference" maxlength="255"></label><label>Referenz Fremdbank<input name="foreignBankReference" maxlength="255"></label><label>Antragsteller<input name="applicant" maxlength="255"></label><label>Begünstigter<input name="beneficiary" maxlength="255"></label><label>LC-Betrag<input name="amount" type="number" min="0" step="0.01"></label><label>Währung<input name="currency" maxlength="3" pattern="[A-Z]{3}" placeholder="EUR"></label><label>Verfallsdatum<input name="expiryDate" type="date"></label><label>Dokumenttyp *<select name="documentType" required><option value="">Bitte auswählen</option>${inboxDocumentTypes.map(([value,label])=>`<option value="${value}">${label}</option>`).join('')}</select></label><label>Dokumentdatum<input name="documentDate" type="date"></label><button type="submit">Akte anlegen & Dokument übernehmen</button><p class="error" role="alert"></p></form><small>Weitere LC-Bedingungen anschließend in der Akte ergänzen. Keine automatische SWIFT-Übernahme.</small></details>`;
}
document.addEventListener('submit',async event=>{
 const form=event.target;if(!form.matches('[data-inbox-new-case]'))return;event.preventDefault();
 const values=Object.fromEntries(new FormData(form));values.reference=values.reference.trim();
 if(!values.reference){form.querySelector('.error').textContent='Bitte eine LC-Referenz angeben.';return;}
 if(!confirm(`Neue LC-Akte „${values.reference}“ anlegen und Dokument übernehmen?`))return;
 const button=form.querySelector('button');button.disabled=true;form.querySelector('.error').textContent='';
 try{
  ['ownBankReference','foreignBankReference','applicant','beneficiary','amount','currency','expiryDate','documentDate'].forEach(key=>{if(!values[key])values[key]=null;});
  const result=await json(`/api/inbox/${form.dataset.inboxNewCase}/new-case`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(values)});
  $('#inboxMessage').textContent=`LC-Akte „${values.reference}“ angelegt und Dokument gespeichert.`;
  await load();await loadInbox();
 }catch(error){form.querySelector('.error').textContent=error.message;button.disabled=false;}
});
