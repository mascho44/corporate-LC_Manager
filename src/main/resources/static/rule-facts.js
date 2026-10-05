(() => {
 const labels={
  DOCUMENT_ISSUER:'Dokumentaussteller',DOCUMENT_RECIPIENT:'Dokumentempfänger',DOCUMENT_GOODS_DESCRIPTION:'Warenbeschreibung im Dokument',
  LC_RULE_STANDARD:'Anzuwendender Regelstandard',LC_TRANSFERRED:'Ist dieses LC tatsächlich übertragen?',LC_SECOND_BENEFICIARY:'Zweiter Begünstigter',LC_GOODS_DESCRIPTION:'Gültige LC-Warenbeschreibung'
 };
 let sequence=0;
 async function openFacts(documentId){
  const lcId=activeLc?.id;if(!lcId)return;
  const current=++sequence,editing=documentId?'DOCUMENT_UPLOAD':'LC_EDIT',allowed=can(editing);
  const url='/api/lcs/'+lcId+(documentId?'/documents/'+documentId:'')+'/rule-facts';
  try{
   const values=await json(url);if(current!==sequence||activeLc?.id!==lcId)return;
   let dialog=document.getElementById('ruleFactsDialog');
   if(!dialog){dialog=document.createElement('dialog');dialog.id='ruleFactsDialog';dialog.className='wide-dialog';document.body.append(dialog);}
   if(dialog.open)dialog.close();
   const keys=documentId?['DOCUMENT_ISSUER','DOCUMENT_RECIPIENT','DOCUMENT_GOODS_DESCRIPTION']:['LC_RULE_STANDARD','LC_TRANSFERRED','LC_SECOND_BENEFICIARY','LC_GOODS_DESCRIPTION'];
   dialog.innerHTML='<form><div class="dialoghead"><h2>'+ (documentId?'Dokument-Prüfdaten':'LC-Prüfkontext')+'</h2><button type="button" data-close class="ghost">×</button></div><p>Nur fachlich geprüfte Angaben erfassen. Leer bedeutet unbekannt, nicht „nein“. Änderungen setzen bisherige Prüfentscheidungen zurück.</p><div class="form-grid">'+keys.map(key=>{
    let input;
    if(key==='LC_RULE_STANDARD'||key==='LC_TRANSFERRED'){
     const choices=key==='LC_RULE_STANDARD'?[['UCP600','UCP 600'],['OTHER','Anderer Regelstandard']]:[['true','Ja, tatsächlich übertragen'],['false','Nein, nicht übertragen']];
     input='<select name="'+key+'"><option value="">Unbekannt / nicht geprüft</option>'+choices.map(([v,l])=>'<option value="'+v+'" '+(values[key]===v?'selected':'')+'>'+l+'</option>').join('')+'</select>';
    }else input='<textarea name="'+key+'" maxlength="'+(key.includes('GOODS_DESCRIPTION')?4000:500)+'">'+esc(values[key]||'')+'</textarea>';
    return '<label class="wide">'+labels[key]+input+'</label>';
   }).join('')+'</div><p class="error" data-message role="alert"></p><div class="actions">'+(documentId?'<button type="button" class="secondary" data-context>LC-Prüfkontext</button>':'')+'<button type="button" class="secondary" data-close>Schließen</button>'+(allowed?'<button type="submit">Prüfdaten speichern</button>':'')+'</div></form>';
   const form=dialog.querySelector('form');
   if(!allowed)form.querySelectorAll('textarea,select').forEach(input=>input.disabled=true);
   dialog.querySelectorAll('[data-close]').forEach(button=>button.onclick=()=>{++sequence;dialog.close();});
   const context=dialog.querySelector('[data-context]');if(context)context.onclick=()=>openFacts(null);
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
 document.addEventListener('click',event=>{const button=event.target.closest('[data-rule-facts]');if(button)openFacts(button.dataset.ruleFacts);});
})();
