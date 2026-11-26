/* Stamps and signatures per page: a person decides, the decisions are kept per tenant (later the basis for suggestions). */
(function(){
 const KINDS=[
  ['STAMP',tx('pm.kind.STAMP','Stempel')],
  ['SIGNATURE',tx('pm.kind.SIGNATURE','Unterschrift')],
  ['SIGNED_STAMP',tx('pm.kind.SIGNED_STAMP','Unterschriebener Stempel')],
  ['NONE',tx('pm.kind.NONE','Weder noch')]
 ];
 const label=kind=>(KINDS.find(k=>k[0]===kind)||[kind,kind])[1];
 const headers={'Content-Type':'application/json'};
 let dialog=null;

 function open(documentId,name){
  if(dialog)dialog.close();
  const editable=typeof can==='function'&&can('DOCUMENT_REVIEW');
  const state={page:1,pageCount:null,marks:[],candidates:[],kind:'STAMP',imageUrl:null,version:0};
  dialog=document.createElement('dialog');dialog.className='page-marks-dialog';
  const esc=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  dialog.innerHTML=`<div class="dialoghead"><h2>${esc(tx('pm.title','Stempel und Unterschriften'))}: ${esc(name||'')}</h2><button type="button" data-pm-close>${esc(tx('pm.close','Schließen'))}</button></div>
   <p class="pm-help">${esc(editable?tx('pm.help','Bereich anklicken oder mit der Maus aufziehen, vorher die Art wählen. Eine gesetzte Markierung entfernen Sie mit einem Klick darauf.'):tx('pm.helpReadonly','Markierungen dieses Dokuments (nur Ansicht).'))}</p>
   <div class="pm-kinds" role="radiogroup" ${editable?'':'hidden'}>${KINDS.map(([k,l])=>`<label class="pm-kind pm-${k}"><input type="radio" name="pm-kind" value="${k}" ${k===state.kind?'checked':''}> ${esc(l)}</label>`).join('')}</div>
   <div class="pm-nav"><button type="button" data-pm-prev aria-label="${esc(tx('pm.prev','Vorherige Seite'))}">←</button><span data-pm-page></span><button type="button" data-pm-next aria-label="${esc(tx('pm.next','Nächste Seite'))}">→</button></div>
   <p class="pm-status" data-pm-status></p>
   <div class="pm-wrap" data-pm-wrap hidden><img alt="" data-pm-image><div class="pm-layer" data-pm-layer></div></div>`;
  document.body.append(dialog);
  const q=s=>dialog.querySelector(s),image=q('[data-pm-image]'),layer=q('[data-pm-layer]'),wrap=q('[data-pm-wrap]'),status=q('[data-pm-status]'),pageLabel=q('[data-pm-page]'),prev=q('[data-pm-prev]'),next=q('[data-pm-next]');
  dialog.addEventListener('close',()=>{if(state.imageUrl)URL.revokeObjectURL(state.imageUrl);dialog.remove();if(dialog&&dialog.isConnected===false)dialog=null;});
  q('[data-pm-close]').onclick=()=>dialog.close();
  dialog.querySelectorAll('input[name="pm-kind"]').forEach(r=>r.onchange=()=>{state.kind=r.value;});
  const controls=()=>{prev.disabled=state.page<=1;next.disabled=state.pageCount!=null&&state.page>=state.pageCount;pageLabel.textContent=tx('pm.pageOf','Seite {n}',{n:state.page})+(state.pageCount?' / '+state.pageCount:'');};
  const place=(el,b)=>{el.style.left=b.x*100+'%';el.style.top=b.y*100+'%';el.style.width=b.w*100+'%';el.style.height=b.h*100+'%';};
  const overlaps=(a,b)=>{const w=Math.min(a.x+a.w,b.x+b.w)-Math.max(a.x,b.x),h=Math.min(a.y+a.h,b.y+b.h)-Math.max(a.y,b.y);return w>0&&h>0&&w*h>0.5*Math.min(a.w*a.h,b.w*b.h);};
  async function save(box){
   try{const mark=await json(`/api/documents/${encodeURIComponent(documentId)}/marks`,{method:'POST',headers,body:JSON.stringify({page:state.page,...box,kind:state.kind})});state.marks.push(mark);render();}
   catch(error){status.textContent=error.message;}
  }
  async function remove(mark){
   try{await json(`/api/documents/${encodeURIComponent(documentId)}/marks/${encodeURIComponent(mark.id)}`,{method:'DELETE'});state.marks=state.marks.filter(m=>m.id!==mark.id);render();}
   catch(error){status.textContent=error.message;}
  }
  function render(){
   layer.replaceChildren();
   const marks=state.marks.filter(m=>m.page===state.page);
   state.candidates.filter(c=>c.page===state.page&&!marks.some(m=>overlaps(m,c))).forEach(c=>{
    const el=document.createElement('button');el.type='button';el.className='pm-candidate';el.title=tx('pm.candidate','Mögliche Stelle – anklicken, um sie mit der gewählten Art zu markieren');place(el,c);
    el.disabled=!editable;el.onclick=event=>{event.stopPropagation();save({x:c.x,y:c.y,w:c.w,h:c.h});};layer.append(el);
   });
   marks.forEach(m=>{
    const el=document.createElement('button');el.type='button';el.className='pm-mark pm-'+m.kind;place(el,m);
    const tag=document.createElement('span');tag.textContent=label(m.kind);el.append(tag);
    el.title=editable?tx('pm.remove','Markierung entfernen'):label(m.kind);el.disabled=!editable;el.onclick=event=>{event.stopPropagation();remove(m);};layer.append(el);
   });
  }
  // Drawing a new region with the mouse.
  if(editable){
   let start=null,rubber=null;
   const point=event=>{const r=layer.getBoundingClientRect();return {x:Math.min(1,Math.max(0,(event.clientX-r.left)/r.width)),y:Math.min(1,Math.max(0,(event.clientY-r.top)/r.height))};};
   layer.addEventListener('pointerdown',event=>{if(event.target!==layer)return;start=point(event);rubber=document.createElement('div');rubber.className='pm-rubber';layer.append(rubber);layer.setPointerCapture(event.pointerId);});
   layer.addEventListener('pointermove',event=>{if(!start)return;const p=point(event);place(rubber,{x:Math.min(start.x,p.x),y:Math.min(start.y,p.y),w:Math.abs(p.x-start.x),h:Math.abs(p.y-start.y)});});
   const finish=event=>{if(!start)return;const p=point(event),box={x:Math.min(start.x,p.x),y:Math.min(start.y,p.y),w:Math.abs(p.x-start.x),h:Math.abs(p.y-start.y)};start=null;rubber?.remove();rubber=null;if(box.w>=0.01&&box.h>=0.01&&box.x<1&&box.y<1)save(box);};
   layer.addEventListener('pointerup',finish);layer.addEventListener('pointercancel',()=>{start=null;rubber?.remove();rubber=null;});
  }
  async function loadPage(number){
   const request=++state.version;state.page=number;controls();wrap.hidden=true;status.textContent=tx('pm.loading','Seite {n} wird geladen …',{n:number});
   try{
    const response=await fetch(`/api/documents/${encodeURIComponent(documentId)}/pages/${number}/preview`);
    if(!response.ok||!response.headers.get('Content-Type')?.startsWith('image/png'))throw Error(tx('pm.noPreview','Seitenvorschau nicht verfügbar.'));
    const blob=await response.blob();if(request!==state.version||!dialog?.open)return;
    const count=Number(response.headers.get('X-Page-Count'));if(Number.isInteger(count)&&count>=number)state.pageCount=count;
    if(state.imageUrl)URL.revokeObjectURL(state.imageUrl);state.imageUrl=URL.createObjectURL(blob);
    image.onload=()=>{if(request===state.version){wrap.hidden=false;status.textContent='';render();}};image.src=state.imageUrl;controls();
   }catch(error){status.textContent=error.message;}
  }
  prev.onclick=()=>loadPage(state.page-1);next.onclick=()=>loadPage(state.page+1);
  dialog.showModal();controls();
  Promise.all([
   json(`/api/documents/${encodeURIComponent(documentId)}/marks`).catch(()=>[]),
   editable?json(`/api/documents/${encodeURIComponent(documentId)}/mark-candidates`).catch(()=>[]):Promise.resolve([])
  ]).then(([marks,candidates])=>{state.marks=Array.isArray(marks)?marks:[];state.candidates=Array.isArray(candidates)?candidates:[];loadPage(1);});
 }

 globalThis.LcPageMarks={open};

 const base=typeof documentRow==='function'?documentRow:null;
 if(base){
  globalThis.documentRow=function(d){
   const html=base(d);
   if(!/\.pdf$/i.test(d.originalFilename||''))return html;
   const button=`<button type="button" class="secondary" data-page-marks="${String(d.id).replace(/[^0-9a-f-]/gi,'')}" data-document-name="${String(d.originalFilename).replace(/"/g,'&quot;')}">${tx('pm.open','Stempel & Unterschriften')}</button>`;
   return html.replace('<div class="document-quick-actions">','<div class="document-quick-actions">'+button);
  };
  document.addEventListener('click',event=>{const b=event.target.closest?.('[data-page-marks]');if(b)open(b.dataset.pageMarks,b.dataset.documentName);});
 }
})();
