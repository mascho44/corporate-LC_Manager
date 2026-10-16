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
   dialog.innerHTML='<form><div class="dialoghead"><h2>'+esc(title)+'</h2><button type="button" data-close class="ghost">×</button></div><p>Nur fachlich geprüfte Angaben erfassen. Leer bedeutet unbekannt, nicht „nein“. Änderungen setzen bisherige Prüfentscheidungen zurück. Die Dokumentensatzkennung muss bei zusammengehörigen Dokumenten übereinstimmen.</p>'+Array.from(groups,([name,items])=>'<details class="rule-facts-section" open><summary>'+esc(name)+'</summary><div class="form-grid">'+items.map(d=>'<label class="'+(d.field.includes('GOODS_DESCRIPTION')||d.field.endsWith('RISKS')?'wide':'')+'">'+esc(d.label)+widget(d,values[d.field])+'</label>').join('')+'</div></details>').join('')+'<p class="error" data-message role="alert"></p><div class="actions">'+(documentId||requirements?'<button type="button" class="secondary" data-context>LC-Prüfkontext</button>':'')+(documentType&&!requirements?'<button type="button" class="secondary" data-requirements>LC-Anforderungen für diesen Dokumenttyp</button>':'')+(documentId&&!requirements?'<button type="button" class="secondary" data-learned-labels>Gelernte Beschriftungen</button>':'')+'<button type="button" class="secondary" data-close>Schließen</button>'+(allowed?'<button type="submit">Prüfdaten speichern</button>':'')+'</div></form>';
   const form=dialog.querySelector('form');
   // Values filled automatically from the recognised text: marked until a person saves (which confirms them).
   if(!requirements){
    const autoList=await json(url+'/auto').catch(()=>[]),auto=new Set(Array.isArray(autoList)?autoList:[]);
    if(auto.size){
     const note=document.createElement('p');note.className='auto-fact-note';note.textContent=auto.size+' Angabe'+(auto.size===1?'':'n')+' automatisch aus dem erkannten Text übernommen (markiert „aus OCR“). Bitte prüfen; Speichern bestätigt sie.';
     form.querySelector('p')?.after(note);
     auto.forEach(name=>{const control=form.elements[name];if(!control||!control.closest)return;control.classList.add('auto-fact');const label=control.closest('label');if(label&&!label.querySelector('.auto-fact-badge')){const badge=document.createElement('small');badge.className='auto-fact-badge';badge.textContent='aus OCR, unbestätigt';label.append(badge);}});
    }
   }
   if(missing){
    const box=document.createElement('div'),note=document.createElement('p'),button=document.createElement('button');
    note.textContent=missing.size+' noch fehlende Dokumentangaben für aktive Rule Packs. Dies sind mögliche Prüfeingaben, keine pauschalen Pflichtangaben; die fachliche Anwendbarkeit muss geprüft werden.';
    button.type='button';button.className='secondary';button.textContent='Nur fehlende Angaben anzeigen';let filtered=false;
    button.onclick=()=>{filtered=!filtered;fields.forEach(d=>{const label=form.elements[d.field].closest('label');label.hidden=filtered&&!missing.has(d.field);});form.querySelectorAll('.rule-facts-section').forEach(group=>{group.hidden=filtered&&![...group.querySelectorAll('label')].some(label=>!label.hidden);});button.textContent=filtered?'Alle Prüfdaten anzeigen':'Nur fehlende Angaben anzeigen';};
    box.append(note,button);form.querySelector('.dialoghead').after(box);
   }
   if(!requirements&&allowed){
    try{
     const raw=documentId?await json(url+'/suggestions'):(await json(base+'/rule-facts/suggestions')).lc;
     const suggestions=Array.isArray(raw)?raw:[];
     if(current===sequence&&suggestions.length){
      const box=document.createElement('div');box.className='rule-facts-suggestions';
      const head=document.createElement('p');head.textContent=(documentId?'Vorschläge aus dem Dokument':'Vorschläge aus den Parteiangaben der LC')+' (bitte prüfen, nichts wird automatisch gespeichert):';box.append(head);
      const byField=Object.fromEntries(fields.map(d=>[d.field,d]));
      suggestions.forEach(s=>{
       const d=byField[s.field];if(!d||!form.elements[s.field])return;
       const b=document.createElement('button');b.type='button';b.className='secondary';
       const shown=s.value==='true'?'Ja':s.value==='false'?'Nein':s.value;
       b.textContent=d.label+': '+shown+' übernehmen';b.title=s.source;
       b.onclick=()=>{form.elements[s.field].value=s.value;b.disabled=true;};
       box.append(b);
      });
      if(box.querySelector('button'))form.querySelector('.dialoghead').after(box);
     }
    }catch(ignored){}
   }
   if(!requirements&&allowed&&documentId){
    try{
     const ai=await json('/api/ai/status');
     if(ai.enabled&&current===sequence){
      const box=document.createElement('div');box.className='rule-facts-suggestions';
      const ask=document.createElement('button'),note=document.createElement('small');ask.type='button';ask.className='secondary';ask.textContent='KI-Vorschläge holen (lokales Modell)';
      ask.onclick=async()=>{
       ask.disabled=true;ask.textContent='KI liest das Dokument … (kann eine Minute dauern)';
       try{
        const found=await json(url+'/ai-suggestions',{method:'POST'});const byField=Object.fromEntries(fields.map(d=>[d.field,d]));let shownCount=0;
        found.forEach(s=>{const d=byField[s.field];if(!d||!form.elements[s.field])return;shownCount++;
         const b=document.createElement('button');b.type='button';b.className='secondary';b.textContent='KI: '+d.label+': '+s.value+' übernehmen';b.title=s.source;
         b.onclick=()=>{form.elements[s.field].value=s.value;b.disabled=true;};box.append(b);});
        note.textContent=shownCount?'KI-Vorschläge mit geprüftem Zitat – bitte fachlich prüfen, nichts wird automatisch gespeichert.':'Die KI hat keine belegbaren Vorschläge gefunden.';
        ask.remove();box.append(note);
       }catch(error){ask.disabled=false;ask.textContent='KI-Vorschläge holen (lokales Modell)';note.textContent='KI nicht erreichbar: '+error.message;if(!note.isConnected)box.append(note);}
      };
      box.append(ask);form.querySelector('.dialoghead').after(box);
     }
    }catch(ignored){}
   }
   if(!allowed)form.querySelectorAll('input,textarea,select').forEach(input=>input.disabled=true);
   dialog.querySelectorAll('[data-close]').forEach(button=>button.onclick=()=>{++sequence;dialog.close();});
   const learnedButton=dialog.querySelector('[data-learned-labels]');if(learnedButton)learnedButton.onclick=()=>openLearnedLabels(documentType);
   const context=dialog.querySelector('[data-context]');if(context)context.onclick=()=>openFacts(null,documentType);
   const requirementButton=dialog.querySelector('[data-requirements]');if(requirementButton)requirementButton.onclick=()=>openFacts(null,documentType,true);
   form.onsubmit=async event=>{
    event.preventDefault();if(!allowed)return;
    const button=form.querySelector('[type="submit"]');if(button.disabled)return;button.disabled=true;
    const payload=Object.fromEntries(keys.map(key=>[key,form.elements[key].value||null]));
    try{await json(url,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});dialog.close();if(typeof CustomEvent==='function')document.dispatchEvent?.(new CustomEvent('lc-rule-facts-saved'));if(activeLc?.id===lcId)await (typeof showKeepingView==='function'?showKeepingView:show)(lcId);}
    catch(error){dialog.querySelector('[data-message]').textContent=error.message;}
    finally{button.disabled=false;}
   };
   dialog.showModal();
  }catch(error){alert('Prüfdaten konnten nicht geladen werden: '+error.message);}
 }
 /** Labels learned from values entered by hand (per document type); a wrong one can be removed. */
 async function openLearnedLabels(documentType){
  const box=document.createElement('dialog');box.className='wide-dialog';document.body.append(box);box.onclose=()=>box.remove();
  box.innerHTML='<div class="dialoghead"><h2>Gelernte Beschriftungen</h2><button type="button" class="ghost" data-close>×</button></div><p>Trägst du in den Prüfdaten eines Dokuments einen Wert von Hand ein, merkt sich das System die Beschriftung, unter der er gedruckt ist (z. B. „Description of goods“), für diesen Dokumenttyp. Bei späteren Dokumenten wird der Wert dann als Vorschlag erkannt – nur wenn er dort eindeutig ist.</p><div data-list>Wird geladen …</div>';
  box.querySelector('[data-close]').onclick=()=>box.close();box.showModal();
  const list=box.querySelector('[data-list]'),removable=can('DOCUMENT_UPLOAD');
  const render=async()=>{
   try{
    const rows=(await json('/api/learned-labels')).sort((a,b)=>(a.document_type===documentType?0:1)-(b.document_type===documentType?0:1));
    if(!rows.length){list.textContent='Noch nichts gelernt.';return;}
    list.innerHTML='<table class="compact-table"><thead><tr><th>Dokumenttyp</th><th>Prüffeld</th><th>Beschriftung</th><th>Gelernt von</th><th></th></tr></thead><tbody>'+rows.map(r=>'<tr><td>'+esc(r.document_type)+'</td><td>'+esc(r.field)+'</td><td>„'+esc(r.label)+'“</td><td>'+esc(r.learned_by)+'</td><td>'+(removable?'<button type="button" class="ghost danger-text" data-forget="'+esc(r.id)+'">Vergessen</button>':'')+'</td></tr>').join('')+'</tbody></table>';
    list.querySelectorAll('[data-forget]').forEach(b=>b.onclick=async()=>{b.disabled=true;try{await json('/api/learned-labels/'+b.dataset.forget,{method:'DELETE'});await render();}catch(error){b.disabled=false;alert('Nicht entfernt: '+error.message);}});
   }catch(error){list.textContent='Nicht verfügbar: '+error.message;}
  };
  render();
 }
 async function applyAllSuggestions(){
  const lcId=activeLc?.id;if(!lcId)return;
  try{
   const [overview,definitions]=await Promise.all([json('/api/lcs/'+lcId+'/rule-facts/suggestions'),json('/api/lcs/'+lcId+'/rule-facts/definitions')]);
   const label={};[...definitions.document,...definitions.lc].forEach(d=>{label[d.field]=d.label;});
   const fresh=list=>list.filter(x=>!x.current);
   const lcNew=fresh(overview.lc),docs=overview.documents.map(d=>({name:d.filename,items:fresh(d.suggestions)})).filter(d=>d.items.length);
   const total=lcNew.length+docs.reduce((n,d)=>n+d.items.length,0);
   if(!total){alert('Es gibt keine neuen Vorschläge. Bereits erfasste Angaben werden nie überschrieben.');return;}
   const lines=[];if(lcNew.length)lines.push('LC: '+lcNew.map(x=>(label[x.field]||x.field)+' = '+x.value).join('; '));
   docs.slice(0,8).forEach(d=>lines.push(d.name+': '+d.items.map(x=>(label[x.field]||x.field)+' = '+x.value).join('; ')));
   if(docs.length>8)lines.push('… und '+(docs.length-8)+' weitere Dokumente');
   const ok=await confirmAction(total+' erkannte Angaben übernehmen? Nur leere Felder werden gefüllt, eingetragene Werte bleiben unverändert. Bisherige Prüfentscheidungen dieser Akte werden zurückgesetzt.\n\n'+lines.join('\n'));
   if(!ok)return;
   const result=await json('/api/lcs/'+lcId+'/rule-facts/suggestions/apply',{method:'POST'});
   alert(result.lcFields+' LC-Angaben und '+result.documentFields+' Dokumentangaben in '+result.documents+' Dokumenten übernommen.');
   if(activeLc?.id===lcId){await show(lcId);if(typeof activateDossierSection==='function')activateDossierSection('checks');}
  }catch(error){alert('Vorschläge konnten nicht übernommen werden: '+error.message);}
 }
 document.addEventListener('click',event=>{const apply=event.target.closest('[data-apply-suggestions]');if(apply&&apply.hasAttribute?.('data-apply-suggestions')){applyAllSuggestions();return;}const button=event.target.closest('[data-rule-facts]');if(button)openFacts(button.dataset.ruleFacts,button.dataset.ruleFactsType||null);});
})();
