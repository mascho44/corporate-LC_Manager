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

 function open(documentId,name,basePath){
  const base=basePath||`/api/documents/${encodeURIComponent(documentId)}`;
  if(dialog)dialog.close();
  const editable=typeof can==='function'&&can('DOCUMENT_REVIEW');
  const state={page:1,pageCount:null,marks:[],candidates:[],kind:'STAMP',imageUrl:null,version:0};
  dialog=document.createElement('dialog');dialog.className='page-marks-dialog';
  const esc=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  dialog.innerHTML=`<div class="dialoghead"><h2>${esc(tx('pm.title','Stempel und Unterschriften'))}: ${esc(name||'')}</h2><button type="button" data-pm-close>${esc(tx('pm.close','Schließen'))}</button></div>
   <p class="pm-help">${esc(editable?tx('pm.help','Bereich anklicken oder mit der Maus aufziehen, vorher die Art wählen. Eine gesetzte Markierung entfernen Sie mit einem Klick darauf.'):tx('pm.helpReadonly','Markierungen dieses Dokuments (nur Ansicht).'))}</p>
   <div class="pm-kinds" role="radiogroup" ${editable?'':'hidden'}>${KINDS.map(([k,l])=>`<label class="pm-kind pm-${k}"><input type="radio" name="pm-kind" value="${k}" ${k===state.kind?'checked':''}> ${esc(l)}</label>`).join('')}</div>
   <div class="pm-review" data-pm-review hidden></div>
   <div class="pm-nav"><button type="button" class="secondary" data-pm-start ${editable?'':'hidden'}>${esc(tx('pm.reviewStart','Vorschläge prüfen'))}</button><button type="button" data-pm-prev aria-label="${esc(tx('pm.prev','Vorherige Seite'))}">←</button><span data-pm-page></span><button type="button" data-pm-next aria-label="${esc(tx('pm.next','Nächste Seite'))}">→</button></div>
   <p class="pm-status" data-pm-status></p>
   <div class="pm-wrap" data-pm-wrap hidden><img alt="" data-pm-image><div class="pm-layer" data-pm-layer></div></div>`;
  document.body.append(dialog);
  const q=s=>dialog.querySelector(s),image=q('[data-pm-image]'),layer=q('[data-pm-layer]'),wrap=q('[data-pm-wrap]'),status=q('[data-pm-status]'),pageLabel=q('[data-pm-page]'),prev=q('[data-pm-prev]'),next=q('[data-pm-next]');
  dialog.addEventListener('close',()=>{if(state.imageUrl)URL.revokeObjectURL(state.imageUrl);dialog.remove();if(dialog&&dialog.isConnected===false)dialog=null;});
  q('[data-pm-close]').onclick=()=>dialog.close();
  dialog.querySelectorAll('input[name="pm-kind"]').forEach(r=>r.onchange=()=>{state.kind=r.value;});
  const controls=()=>{prev.disabled=state.page<=1;next.disabled=state.pageCount!=null&&state.page>=state.pageCount;pageLabel.textContent=tx('pm.pageOf','Seite {n}',{n:state.page})+(state.pageCount?' / '+state.pageCount:'');};
  const place=(el,b)=>{el.style.left=b.x*100+'%';el.style.top=b.y*100+'%';el.style.width=b.w*100+'%';el.style.height=b.h*100+'%';};
  const usable=c=>c.w<=0.6&&c.h<=0.4&&c.w*c.h<=0.15;
  const overlaps=(a,b)=>{const w=Math.min(a.x+a.w,b.x+b.w)-Math.max(a.x,b.x),h=Math.min(a.y+a.h,b.y+b.h)-Math.max(a.y,b.y);return w>0&&h>0&&w*h>0.5*Math.min(a.w*a.h,b.w*b.h);};
  async function save(box){
   try{const mark=await json(`${base}/marks`,{method:'POST',headers,body:JSON.stringify({page:state.page,...box,kind:state.kind})});state.marks.push(mark);render();}
   catch(error){status.textContent=error.message;}
  }
  async function remove(mark){
   try{await json(`${base}/marks/${encodeURIComponent(mark.id)}`,{method:'DELETE'});state.marks=state.marks.filter(m=>m.id!==mark.id);render();}
   catch(error){status.textContent=error.message;}
  }
  let review=null;
  function render(){
   layer.replaceChildren();
   const marks=state.marks.filter(m=>m.page===state.page);
   state.candidates.filter(c=>c.page===state.page&&usable(c)&&!marks.some(m=>overlaps(m,c))).forEach(c=>{
    const el=document.createElement('button');el.type='button';el.className='pm-candidate';el.title=tx('pm.candidate','Mögliche Stelle – anklicken, um sie mit der gewählten Art zu markieren');place(el,c);
    el.disabled=!editable;if(review&&review.current===c)el.classList.add('pm-current');el.onclick=event=>{event.stopPropagation();save({x:c.x,y:c.y,w:c.w,h:c.h});};layer.append(el);
   });
   marks.forEach(m=>{
    const el=document.createElement('button');el.type='button';el.className='pm-mark pm-'+m.kind;place(el,m);
    const tag=document.createElement('span');tag.textContent=label(m.kind);el.append(tag);
    el.title=editable?tx('pm.remove','Markierung entfernen'):label(m.kind);el.disabled=!editable;el.onclick=event=>{event.stopPropagation();remove(m);};layer.append(el);
   });
  }
  // Drawing a new region with the mouse.
  if(editable){
   let start=null,rubber=null,from=null;
   const point=event=>{const r=layer.getBoundingClientRect();return {x:Math.min(1,Math.max(0,(event.clientX-r.left)/r.width)),y:Math.min(1,Math.max(0,(event.clientY-r.top)/r.height))};};
   layer.addEventListener('pointerdown',event=>{if(event.target!==layer&&!event.target.classList.contains('pm-candidate'))return;from=event.target;start=point(event);rubber=document.createElement('div');rubber.className='pm-rubber';layer.append(rubber);layer.setPointerCapture(event.pointerId);});
   layer.addEventListener('pointermove',event=>{if(!start)return;const p=point(event);place(rubber,{x:Math.min(start.x,p.x),y:Math.min(start.y,p.y),w:Math.abs(p.x-start.x),h:Math.abs(p.y-start.y)});});
   const finish=event=>{if(!start)return;const p=point(event),box={x:Math.min(start.x,p.x),y:Math.min(start.y,p.y),w:Math.abs(p.x-start.x),h:Math.abs(p.y-start.y)};start=null;rubber?.remove();rubber=null;if(box.w>=0.01&&box.h>=0.01&&box.x<1&&box.y<1)save(box);else if(from&&from.classList.contains('pm-candidate'))from.click();from=null;};
   layer.addEventListener('pointerup',finish);layer.addEventListener('pointercancel',()=>{start=null;rubber?.remove();rubber=null;});
  }
  // Review: the application proposes a region, the person answers. A guessed kind (guess) is confirmed with yes/no; without a guess the kind is asked.
  const reviewBox=q('[data-pm-review]');
  function queue(){return state.candidates.filter(c=>usable(c)&&!state.marks.some(m=>m.page===c.page&&overlaps(m,c))).sort((a,b)=>a.page-b.page||a.y-b.y||a.x-b.x);}
  async function decide(kind){
   const c=review.current;
   try{const mark=await json(`${base}/marks`,{method:'POST',headers,body:JSON.stringify({page:c.page,x:c.x,y:c.y,w:c.w,h:c.h,kind})});state.marks.push(mark);}
   catch(error){status.textContent=error.message;return;}
   advance();
  }
  function advance(){
   review.skipped.add(review.current);
   const next=queue().find(c=>!review.skipped.has(c));
   if(!next){review=null;reviewBox.hidden=true;reviewBox.replaceChildren();status.textContent=tx('pm.reviewDone','Alle Vorschläge geprüft.');render();return;}
   review.current=next;review.asked=false;showStep();
  }
  function showStep(){
   const c=review.current,left=queue().filter(x=>!review.skipped.has(x)).length;
   const btn=(text,fn,css)=>{const b=document.createElement('button');b.type='button';b.textContent=text;if(css)b.className=css;b.onclick=fn;return b;};
   reviewBox.replaceChildren();reviewBox.hidden=false;
   const guess=!review.asked&&c.guess&&KINDS.some(k=>k[0]===c.guess)?c.guess:null;
   const p=document.createElement('p');
   p.textContent=guess?tx('pm.reviewGuess','Ist die markierte Stelle ein {kind}? ({n} Vorschläge offen)',{kind:label(guess),n:left}):tx('pm.reviewAsk','Was ist die markierte Stelle? ({n} Vorschläge offen)',{n:left});
   reviewBox.append(p);
   if(guess){
    reviewBox.append(btn(tx('pm.yes','Ja'),()=>decide(guess)),btn(tx('pm.no','Nein'),()=>{review.asked=true;showStep();},'secondary'));
   }else{
    KINDS.forEach(([k,l])=>reviewBox.append(btn(k==='NONE'?tx('pm.reviewNone','Nichts davon'):l,()=>decide(k),k==='NONE'?'secondary':'')));
    reviewBox.append(btn(tx('pm.skip','Überspringen'),advance,'ghost'));
   }
   reviewBox.append(btn(tx('pm.reviewStop','Beenden'),()=>{review=null;reviewBox.hidden=true;render();},'ghost'));
   if(state.page!==c.page)loadPage(c.page);else render();
  }
  q('[data-pm-start]').onclick=()=>{
   review={current:null,skipped:new Set(),asked:false};
   const first=queue()[0];
   if(!first){review=null;status.textContent=tx('pm.reviewNone2','Keine Vorschläge für dieses Dokument.');return;}
   review.current=first;showStep();
  };
  async function loadPage(number){
   const request=++state.version;state.page=number;controls();wrap.hidden=true;status.textContent=tx('pm.loading','Seite {n} wird geladen …',{n:number});
   try{
    const response=await fetch(`${base}/pages/${number}/preview`);
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
   json(`${base}/marks`).catch(()=>[]),
   editable?json(`${base}/mark-candidates`).catch(()=>[]):Promise.resolve([])
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
