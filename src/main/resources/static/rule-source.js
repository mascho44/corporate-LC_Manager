/* Regelquelle und Pack-Auswahl je Akte. Standard: wie im Mandanten eingestellt, alle aktiven Packs. */
(() => {
 const MODES = (globalThis.lcLazy || (f => f()))(() => ({BOTH:tx('rsl.401ffa','Beides: eingebaute Prüfungen und aktive Packs'),EMBEDDED:tx('rsl.8d2bd2','Nur eingebaute Prüfungen'),IMPORTED:tx('rsl.857f3a','Nur importierte Packs')}));
 async function openRuleSource(){
  const lcId=activeLc?.id;if(!lcId)return;
  const url='/api/lcs/'+encodeURIComponent(lcId)+'/rule-source',editable=can('LC_EDIT');
  const dialog=document.createElement('dialog');dialog.className='wide-dialog';
  const title=document.createElement('h2');title.textContent='Regelquelle dieser Akte';
  const content=document.createElement('form');content.textContent=tx('rsl.b1d2e3','Lade …');
  const message=document.createElement('p');message.setAttribute('role','alert');message.className='error';
  const close=document.createElement('button');close.type='button';close.className='secondary';close.textContent=tx('rsl.8311b9','Schließen');close.onclick=()=>dialog.close();
  dialog.append(title,content,message,close);document.body.append(dialog);dialog.addEventListener('close',()=>dialog.remove());dialog.showModal();
  try{
   const view=await json(url);if(!dialog.open)return;
   content.replaceChildren();
   const intro=document.createElement('p');
   intro.textContent=tx('rsl.de35d0','Standard des Mandanten: ')+MODES[view.tenantMode]+'. Eine abweichende Auswahl gilt nur für diese Akte.';content.append(intro);
   const modeLabel=document.createElement('label');modeLabel.textContent=tx('rsl.266ea0','Regelquelle ');
   const select=document.createElement('select');select.disabled=!editable;
   const standard=new Option(tx('rsl.caec4b','Standard des Mandanten'),'');select.add(standard);
   Object.entries(MODES).forEach(([value,text])=>select.add(new Option(text,value)));
   select.value=view.override||'';modeLabel.append(select);content.append(modeLabel);
   const packs=document.createElement('fieldset');const legend=document.createElement('legend');legend.textContent=tx('rsl.dccb44','Anzuwendende Packs (keine Auswahl = alle aktiven)');packs.append(legend);
   const selected=new Set(view.selectedPackIds);
   if(!view.activePacks.length){const none=document.createElement('p');none.textContent='Es ist kein Rule Pack aktiv.';packs.append(none);}
   view.activePacks.forEach(pack=>{
    const label=document.createElement('label');label.className='check-row';
    const box=document.createElement('input');box.type='checkbox';box.value=pack.packId;box.checked=selected.has(pack.packId);box.disabled=!editable;
    label.append(box,' '+pack.name+' (v'+pack.version+')');packs.append(label);
   });
   content.append(packs);
   if(editable){
    const save=document.createElement('button');save.type='submit';save.textContent=tx('rsl.b97d23','Speichern');content.append(save);
    content.onsubmit=async event=>{
     event.preventDefault();save.disabled=true;message.textContent='';
     const packIds=[...packs.querySelectorAll('input:checked')].map(box=>box.value);
     try{
      await json(url,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({override:select.value||null,packIds})});
      dialog.close();if(activeLc?.id===lcId)await show(lcId);
     }catch(error){message.textContent=error.message;}
     finally{save.disabled=false;}
    };
   }
  }catch(error){content.textContent=error.message;}
 }
 document.addEventListener('click',event=>{if(event.target.closest('[data-rule-source-open]'))openRuleSource();});
})();
