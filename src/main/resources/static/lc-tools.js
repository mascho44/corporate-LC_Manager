/* Explicit case actions, never dialogs opened directly from the main menu. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? `{${n}}`)); };
 /** The labels are translated when the list is read, so a language change is honoured. */
 const conditions = () => ({
  GOODS_DESCRIPTION: tr('lcx.cond.GOODS_DESCRIPTION', 'Warenbeschreibung'),
  ADDITIONAL_CONDITIONS: tr('lcx.cond.ADDITIONAL_CONDITIONS', 'Zusatzbedingungen'),
  AMOUNT_TOLERANCE: tr('lcx.cond.AMOUNT_TOLERANCE', 'Betragstoleranz'),
  PRESENTATION_PERIOD: tr('lcx.cond.PRESENTATION_PERIOD', 'Vorlagefrist'),
  PARTIAL_SHIPMENTS: tr('lcx.cond.PARTIAL_SHIPMENTS', 'Teillieferungen'),
  TRANSSHIPMENT: tr('lcx.cond.TRANSSHIPMENT', 'Umladung'),
  PLACE_OF_RECEIPT: tr('lcx.cond.PLACE_OF_RECEIPT', 'Übernahmeort'),
  PORT_OF_LOADING: tr('lcx.cond.PORT_OF_LOADING', 'Verladehafen'),
  PORT_OF_DISCHARGE: tr('lcx.cond.PORT_OF_DISCHARGE', 'Entladehafen'),
  FINAL_DESTINATION: tr('lcx.cond.FINAL_DESTINATION', 'Endbestimmungsort'),
  APPLICABLE_RULES: tr('lcx.cond.APPLICABLE_RULES', 'Anwendbare Regeln (z. B. UCP LATEST VERSION)')
 });
 const types = () => ({
  OPENING: tr('lcx.type.OPENING', 'Eröffnung'),
  AMENDMENT: tr('lcx.type.AMENDMENT', 'Änderung'),
  EXAMINATION: tr('lcx.type.EXAMINATION', 'Dokumentenprüfung'),
  ADVISING: tr('lcx.type.ADVISING', 'Avisierung'),
  CONFIRMATION: tr('lcx.type.CONFIRMATION', 'Bestätigung')
 });
 function dialog(title) {
  const d=document.createElement('dialog');d.className='wide-dialog';
  d.innerHTML='<div class="dialoghead"><h2>'+esc(title)+'</h2><button type="button" class="ghost" aria-label="'+esc(tr('common.close','Schließen'))+'">×</button></div><div class="tool-content"></div><p class="error" role="alert"></p>';
  d.querySelector('button').onclick=()=>{d.close();d.remove();};
  d.addEventListener('cancel',()=>d.remove());document.body.append(d);d.showModal();return d;
 }
 async function openConditions(id) {
  const d=dialog(tr('lcx.condTitle','Fachliche LC-Bedingungen'));
  try {
   const before=await json('/api/lcs/'+id+'/conditions');
   const f=document.createElement('form');
   f.innerHTML='<p>'+esc(tr('lcx.condIntro','Diese Werte gelten unabhängig vom Importformat. Leer speichern entfernt eine Bedingung ausdrücklich. MT707-Änderungen aktualisieren die gültige Fassung.'))+'</p><div class="form-grid">'+Object.entries(conditions()).map(([key,label])=>'<label>'+esc(tr('lcx.cond.'+key,label))+'<textarea maxlength="4000" name="'+key+'" '+(can('LC_EDIT')?'':'disabled')+'>'+esc(before[key]||'')+'</textarea></label>').join('')+'</div>'+(can('LC_EDIT')?'<button type="submit">'+esc(tr('lcx.condSave','Geänderte Bedingungen speichern'))+'</button>':'');
   d.querySelector('.tool-content').append(f);
   f.onsubmit=async e=>{
    e.preventDefault();const changed={};
    Object.keys(conditions()).forEach(key=>{const value=f.elements[key].value.trim();if(value!==(before[key]||''))changed[key]=value;});
    if(!Object.keys(changed).length)return;
    if(Object.values(changed).some(v=>!v)&&!confirm(tr('lcx.condConfirm','Leere Bedingungen ausdrücklich entfernen?')))return;
    const button=f.querySelector('button');button.disabled=true;
    try{
     await json('/api/lcs/'+id+'/conditions',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({conditions:changed})});
     d.close();d.remove();await load();if(activeLc?.id===id)await show(id);
    }catch(error){d.querySelector('.error').textContent=error.message;button.disabled=false;}
   };
  }catch(error){d.querySelector('.error').textContent=error.message;}
 }
 async function openCharges(id,currency) {
  const d=dialog(tr('lc.charges','Gebühren & Provisionen'));
  async function render() {
   const [profiles,history]=await Promise.all([json('/api/charge-profiles'),json('/api/lcs/'+id+'/charges')]);
   const available=profiles.filter(p=>p.currency===currency),c=d.querySelector('.tool-content');
   c.innerHTML='<p>'+esc(tr('lcx.chIntro','Schätzung aus frei konfigurierten Tarifen. Keine Bankabrechnung, Steuern, Währungsumrechnung oder automatische Laufzeitberechnung. Die Anzahl gibt Tarif-Einheiten bzw. Ereignisse an.'))+'</p><form class="estimate-form"><label>'+esc(tr('lcx.chProfile','Tarifprofil'))+'<select name="profileId" required>'+available.map(p=>'<option value="'+p.id+'">'+esc(p.name+' · '+(p.bankName||tr('lcx.noBank','ohne Bank'))+' · '+p.currency)+'</option>').join('')+'</select></label><div class="form-grid">'+Object.entries(types()).map(([key,label])=>'<label>'+esc(tr('lcx.type.'+key,label))+' – '+esc(tr('lcx.count','Anzahl'))+'<input name="'+key+'" type="number" min="0" max="10000" step="1" value="0" required></label>').join('')+'</div>'+(can('LC_EDIT')?'<button '+(available.length?'':'disabled')+'>'+esc(tr('lcx.chSave','Schätzung speichern'))+'</button>':'')+'</form>'+(!available.length?'<p>'+esc(tr('lcx.chNoProfile','Für diese LC-Währung ist noch kein Tarifprofil vorhanden.'))+'</p>':'')+'<h3>'+esc(tr('lcx.chSaved','Gespeicherte Schätzungen'))+'</h3><div class="estimates"></div>'+(can('SETTINGS_MANAGE')?'<details><summary>'+esc(tr('lcx.chNew','Neues unveränderliches Tarifprofil anlegen'))+'</summary><form class="profile-form"><div class="form-grid"><label>'+esc(tr('lcx.chName','Profilname / Version'))+'<input name="name" required maxlength="100"></label><label>'+esc(tr('lcx.chBank','Bank'))+'<input name="bankName" maxlength="255"></label><label>'+esc(tr('lcx.chCurrency','Währung'))+'<input name="currency" required pattern="[A-Z]{3}" maxlength="3" value="'+esc(currency||'')+'"></label></div><p>'+esc(tr('lcx.chFormula','Tarif pro Einheit: LC-Betrag × Prozent / 100 + Fixbetrag, begrenzt durch Minimum und optional Maximum. Erst danach mit Anzahl multiplizieren und auf Währungsstellen runden.'))+'</p>'+Object.entries(types()).map(([key,label])=>'<fieldset><legend>'+esc(tr('lcx.type.'+key,label))+'</legend><div class="form-grid">'+[['percent',tr('lcx.percent','Prozent')],['fixed',tr('lcx.fixed','Fixbetrag')],['minimum',tr('lcx.minimum','Minimum')],['maximum',tr('lcx.maximum','Maximum (optional)')]].map(([field,text])=>'<label>'+esc(text)+'<input name="'+key+'_'+field+'" type="number" min="0" '+(field==='percent'?'max="100"':'')+' step="0.000001" '+(field==='maximum'?'':'required value="0"')+'></label>').join('')+'</div></fieldset>').join('')+'<button>'+esc(tr('lcx.chProfileSave','Tarifprofil speichern'))+'</button></form></details>':'');
   const out=c.querySelector('.estimates');
   history.forEach(entry=>{
    const result=JSON.parse(entry.resultJson),profile=JSON.parse(entry.profileSnapshot),row=document.createElement('section');
    row.innerHTML='<h4>'+esc(result.total+' '+result.currency)+' · '+esc(profile.name)+'</h4><p>'+esc(entry.createdAt+' · '+entry.createdBy)+'</p><ul>'+result.lines.map(line=>'<li>'+esc((tr('lcx.type.'+line.type,types()[line.type]||line.type))+' × '+line.units+': '+line.amount+' '+result.currency)+'</li>').join('')+'</ul>';
    out.append(row);
   });
   if(!history.length)out.textContent=tr('lcx.chNone','Noch keine Schätzungen gespeichert.');
   const form=c.querySelector('.estimate-form');form.querySelectorAll('input,select').forEach(el=>el.disabled=!can('LC_EDIT'));
   form.onsubmit=async e=>{
    e.preventDefault();const units={};Object.keys(types()).forEach(key=>units[key]=Number(form.elements[key].value));
    await save(form,'/api/lcs/'+id+'/charges',{profileId:form.elements.profileId.value,units});
   };
   const profile=c.querySelector('.profile-form');
   if(profile)profile.onsubmit=async e=>{
    e.preventDefault();
    const rules=Object.keys(types()).map(type=>({type,...Object.fromEntries(['percent','fixed','minimum','maximum'].map(field=>[field,profile.elements[type+'_'+field].value||null]))}));
    await save(profile,'/api/charge-profiles',{name:profile.elements.name.value,bankName:profile.elements.bankName.value,currency:profile.elements.currency.value,rules});
   };
  }
  async function save(form,url,payload){
   const button=form.querySelector('button');button.disabled=true;d.querySelector('.error').textContent='';
   try{await json(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});await render();}
   catch(error){d.querySelector('.error').textContent=error.message;button.disabled=false;}
  }
  try{await render();}catch(error){d.querySelector('.error').textContent=error.message;}
 }
 const observer=new MutationObserver(()=>{
  const edit=document.querySelector('#editLc');
  if(!edit||document.querySelector('[data-lc-tool]')||!activeLc)return;
  const id=activeLc.id,currency=activeLc.currency;
  for(const [label,action] of [[tx('lc.conditions','LC-Bedingungen'),()=>openConditions(id)],[tx('lc.charges','Gebühren & Provisionen'),()=>openCharges(id,currency)]]){
   const button=document.createElement('button');button.type='button';button.className='secondary';button.dataset.lcTool='true';button.textContent=label;button.onclick=action;edit.before(button);
  }
 });
 observer.observe(document.querySelector('#detail'),{childList:true,subtree:true});
})();
