/* "Neu erkennen": aktuelle Erkennung erneut auf gespeicherte Dokumente anwenden. Zeigt Vorher/Nachher und ändert erst nach Bestätigung. */
(()=>{'use strict';
 // app.js declares these with top-level let: visible by name to other scripts, but not as window properties.
 const currentLc=()=>typeof activeLc!=='undefined'?activeLc:null;
 const csrf=()=>typeof csrfToken!=='undefined'?csrfToken:'';
 const el=(tag,props={},...children)=>{const n=Object.assign(document.createElement(tag),props);n.append(...children);return n;};
 async function call(url,body){
  const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-TOKEN':csrf()},body:JSON.stringify(body||{})});
  const data=await response.json().catch(()=>({}));
  if(!response.ok)throw new Error(data.error||'HTTP '+response.status);
  return data;
 }
 function changesTable(preview){
  const table=el('table',{className:'re-recognition-table'});
  table.append(el('thead',{},el('tr',{},el('th',{textContent:'Feld'}),el('th',{textContent:'Bisher'}),el('th',{textContent:'Neu'}),el('th',{textContent:'Wirkung'}))));
  const body=el('tbody');
  preview.changes.forEach(c=>body.append(el('tr',{},el('th',{textContent:c.label}),el('td',{textContent:c.before??'–'}),el('td',{textContent:c.after??'–'}),el('td',{textContent:c.appliesOnConfirm?(c.note||'wird übernommen'):(c.note||'nur Hinweis')}))));
  table.append(body);return table;
 }
 function dialogFor(title){
  const dialog=el('dialog',{className:'wide-dialog re-recognition-dialog'});
  const close=el('button',{type:'button',className:'ghost',textContent:'×'});close.setAttribute('aria-label','Schließen');
  close.addEventListener('click',()=>dialog.close());dialog.addEventListener('close',()=>dialog.remove());
  dialog.append(el('div',{className:'dialoghead'},el('div',{},el('h2',{textContent:title})),close));
  document.body.append(dialog);return dialog;
 }
 async function recognise(lcId,doc,dialog,section){
  const status=el('p',{role:'status',textContent:`${doc.originalFilename}: wird erkannt …`});section.append(status);
  try{
   const preview=await call(`/api/lcs/${encodeURIComponent(lcId)}/documents/${encodeURIComponent(doc.id)}/re-recognition`);
   status.remove();
   const card=el('article',{className:'card'},el('h3',{textContent:preview.filename}));
   if(!preview.changes.length){card.append(el('p',{textContent:'Keine Änderung: Die aktuelle Erkennung liefert dasselbe Ergebnis.'}));section.append(card);return 0;}
   card.append(changesTable(preview));
   const message=el('small',{role:'status'});
   const apply=el('button',{type:'button',textContent:'Übernehmen',disabled:!preview.anyApplied});
   apply.addEventListener('click',async()=>{
    apply.disabled=true;message.textContent='';
    try{await call(`/api/lcs/${encodeURIComponent(lcId)}/documents/${encodeURIComponent(doc.id)}/re-recognition/apply`,{token:preview.token});apply.textContent='Übernommen';card.dataset.applied='true';
     if(typeof load==='function'&&typeof show==='function'){await load();await show(lcId);}}
    catch(error){message.textContent=error.message;apply.disabled=false;}
   });
   card.append(el('div',{className:'document-quick-actions'},apply),message);section.append(card);return 1;
  }catch(error){status.textContent=`${doc.originalFilename}: ${error.message}`;status.className='error';return 0;}
 }
 async function run(lcId,docs,title){
  const dialog=dialogFor(title);const section=el('div',{className:'re-recognition-body'});
  dialog.append(el('p',{textContent:'Die aktuelle Erkennung wird erneut ausgeführt. Maschinell erkannte Werte werden bei „Übernehmen“ ersetzt; von Hand gepflegte Werte (Datum, Kennzeichnung, Betrag, Währung) werden nur ergänzt, wenn sie leer sind. Den Dokumenttyp ändert die Neuerkennung nie.'}),section);
  dialog.showModal();
  let withChanges=0;
  for(const doc of docs)withChanges+=await recognise(lcId,doc,dialog,section);
  section.append(el('p',{role:'status',textContent:withChanges?`${withChanges} von ${docs.length} Dokumenten mit Änderungen.`:'Fertig: keine Änderungen.'}));
 }
 document.addEventListener('click',event=>{
  const one=event.target.closest?.('[data-document-rerecognize]');
  if(one&&currentLc())return run(currentLc().id,[{id:one.dataset.documentRerecognize,originalFilename:one.dataset.documentName||'Dokument'}],'Dokument neu erkennen');
  const all=event.target.closest?.('[data-document-rerecognize-all]');
  if(all&&currentLc()){const docs=[...document.querySelectorAll('[data-document-rerecognize]')].map(b=>({id:b.dataset.documentRerecognize,originalFilename:b.dataset.documentName||'Dokument'}));if(docs.length)return run(currentLc().id,docs,'Alle Dokumente neu erkennen');}
 });
})();
