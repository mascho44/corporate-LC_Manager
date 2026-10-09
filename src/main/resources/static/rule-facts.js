(() => {
 let sequence=0;
 function widget(d,value){
  const name=d.field,v=value||'';
  if(d.choices.length)return '<select name="'+name+'"><option value="">Unbekannt / nicht geprüft</option>'+d.choices.map(choice=>'<option value="'+esc(choice)+'" '+(v===choice?'selected':'')+'>'+esc(choice==='true'?'Ja':choice==='false'?'Nein':choice)+'</option>').join('')+'</select>';
  if(name.includes('GOODS_DESCRIPTION')||name.endsWith('RISKS'))return '<textarea name="'+name+'" maxlength="'+d.maxLength+'">'+esc(v)+'</textarea>';
  const type=d.kind==='DATE'?'date':d.kind==='NUMBER'?'number':'text';
  return '<input name="'+name+'" type="'+type+'" value="'+esc(v)+'" maxlength="'+d.maxLength+'" '+(type==='number'?'min="0" step="'+(name.endsWith('COUNT')||name.endsWith('DAYS')?'1':'0.000001')+'"':'')+'>';
 }
 function section(d){
  if(/INSUR|FRANCHISE|PERCENTAGE|ENDORSEMENT/.test(d.field))return 'Versicherung';
  if(/DRAWEE|DRAFT_TENOR/.test(d.field))return 'Tratten';
  if(/INCOTERM|FREIGHT/.test(d.field))return 'Handelsklauseln und Fracht';
  if(/CONSIGNEE|NOTIFY|ADDRESS_COUNTRY|SIGNER|SIGNED_FOR/.test(d.field))return 'Parteien, Adressen und Unterschriften';
  if(/SHIPMENT|PRESENTATION|QUANTITY|WEIGHT|TOLERANCE|ON_BOARD|VESSEL|PACKAGE|SHIPPING_MARKS|TRANSSHIPMENT/.test(d.field))return 'Transport, Mengen und Fristen';
  return 'Allgemeine Prüfdaten';
 }
 async function openFacts(documentId,documentType=null,requirements=false){
  const lcId=activeLc?.id;if(!lcId)return;
  const current=++sequence,allowed=can(documentId?'DOCUMENT_UPLOAD':'LC_EDIT');
  const base='/api/lcs/'+lcId;
  const url=base+(requirements?'/rule-requirements/'+documentType:(documentId?'/documents/'+documentId:'')+'/rule-facts');
  try{
   const definitions=await json(base+'/rule-facts/definitions');
   const values=await json(url);if(current!==sequence||activeLc?.id!==lcId)return;
   const fields=definitions[requirements?'requirements':documentId?'document':'lc'],keys=fields.map(d=>d.field);
   const missing=documentId&&!requirements?new Set(await json(url+'/missing')):null;
   if(current!==sequence||activeLc?.id!==lcId)return;
   let dialog=document.getElementById('ruleFactsDialog');
   if(!dialog){dialog=document.createElement('dialog');dialog.id='ruleFactsDialog';dialog.className='wide-dialog';document.body.append(dialog);}
   if(dialog.open)dialog.close();
   const groups=new Map();
   fields.forEach(d=>{const key=section(d);if(!groups.has(key))groups.set(key,[]);groups.get(key).push(d);});
   const title=requirements?'LC-Anforderungen · '+documentType:documentId?'Dokument-Prüfdaten':'LC-Prüfkontext';
   dialog.innerHTML='<form><div class="dialoghead"><h2>'+esc(title)+'</h2><button type="button" data-close class="ghost">×</button></div><p>Nur fachlich geprüfte Angaben erfassen. Leer bedeutet unbekannt, nicht „nein“. Änderungen setzen bisherige Prüfentscheidungen zurück. Die Dokumentensatzkennung muss bei zusammengehörigen Dokumenten übereinstimmen.</p>'+Array.from(groups,([name,items])=>'<details class="rule-facts-section" open><summary>'+esc(name)+'</summary><div class="form-grid">'+items.map(d=>'<label class="'+(d.field.includes('GOODS_DESCRIPTION')||d.field.endsWith('RISKS')?'wide':'')+'">'+esc(d.label)+widget(d,values[d.field])+'</label>').join('')+'</div></details>').join('')+'<p class="error" data-message role="alert"></p><div class="actions">'+(documentId||requirements?'<button type="button" class="secondary" data-context>LC-Prüfkontext</button>':'')+(documentType&&!requirements?'<button type="button" class="secondary" data-requirements>LC-Anforderungen für diesen Dokumenttyp</button>':'')+'<button type="button" class="secondary" data-close>Schließen</button>'+(allowed?'<button type="submit">Prüfdaten speichern</button>':'')+'</div></form>';
   const form=dialog.querySelector('form');
   if(missing){
    const box=document.createElement('div'),note=document.createElement('p'),button=document.createElement('button');
    note.textContent=missing.size+' noch fehlende Dokumentangaben für aktive Rule Packs. Dies sind mögliche Prüfeingaben, keine pauschalen Pflichtangaben; die fachliche Anwendbarkeit muss geprüft werden.';
    button.type='button';button.className='secondary';button.textContent='Nur fehlende Angaben anzeigen';let filtered=false;
    button.onclick=()=>{filtered=!filtered;fields.forEach(d=>{const label=form.elements[d.field].closest('label');label.hidden=filtered&&!missing.has(d.field);});form.querySelectorAll('.rule-facts-section').forEach(group=>{group.hidden=filtered&&![...group.querySelectorAll('label')].some(label=>!label.hidden);});button.textContent=filtered?'Alle Prüfdaten anzeigen':'Nur fehlende Angaben anzeigen';};
    box.append(note,button);form.querySelector('.dialoghead').after(box);
   }
   if(!allowed)form.querySelectorAll('input,textarea,select').forEach(input=>input.disabled=true);
   dialog.querySelectorAll('[data-close]').forEach(button=>button.onclick=()=>{++sequence;dialog.close();});
   const context=dialog.querySelector('[data-context]');if(context)context.onclick=()=>openFacts(null,documentType);
   const requirementButton=dialog.querySelector('[data-requirements]');if(requirementButton)requirementButton.onclick=()=>openFacts(null,documentType,true);
   form.onsubmit=async event=>{
    event.preventDefault();if(!allowed)return;
    const button=form.querySelector('[type="submit"]');if(button.disabled)return;button.disabled=true;
    const payload=Object.fromEntries(keys.map(key=>[key,form.elements[key].value||null]));
    try{await json(url,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});dialog.close();if(activeLc?.id===lcId)await show(lcId);}
    catch(error){dialog.querySelector('[data-message]').textContent=error.message;}
    finally{button.disabled=false;}
   };
   dialog.showModal();
  }catch(error){alert('Prüfdaten konnten nicht geladen werden: '+error.message);}
 }
 document.addEventListener('click',event=>{const button=event.target.closest('[data-rule-facts]');if(button)openFacts(button.dataset.ruleFacts,button.dataset.ruleFactsType||null);});
})();
