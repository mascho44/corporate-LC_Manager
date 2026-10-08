(()=>{'use strict';
 const section=document.getElementById('trainingSection');if(!section)return;
 const panel=document.createElement('div');panel.className='module-actions';
 const heading=document.createElement('h3');heading.textContent='Split training quality';
 const note=document.createElement('p');note.textContent='Observed corrections in this workspace (latest 1,000 measured confirmations). Historical confirmations without a saved proposal are not evaluated. Pattern replay is not general AI learning or proof of document correctness. Original/copy changes are excluded.';
 const refresh=document.createElement('button');refresh.type='button';refresh.textContent='Refresh quality';
 const output=document.createElement('div');output.setAttribute('role','status');panel.append(heading,note,refresh,output);section.append(panel);
 let generation=0;
 const line=value=>{const p=document.createElement('p');p.textContent=value;output.append(p);};
 const ranges=parts=>(parts||[]).map(part=>`${part.fromPage}–${part.toPage}: ${part.documentType.replaceAll('_',' ').toLowerCase()}`).join('; ');
 async function load(){const current=++generation;refresh.disabled=true;output.replaceChildren();line('Loading…');try{
  const response=await fetch('/api/training/document-types/quality',{cache:'no-store'});if(!response.ok)throw new Error('HTTP '+response.status);const report=await response.json();if(current!==generation)return;
  output.replaceChildren();line(`${report.confirmations} confirmations · ${report.measured} measured · ${report.pages} reviewed pages`);
  if(!report.measured){line('No measured confirmations yet.');return;}
  line(`${report.correctedPages} pages with corrected document type · ${report.changedBoundaries} confirmations with changed boundaries`);
  const details=document.createElement('details');const summary=document.createElement('summary');summary.textContent='Recent confirmations';const list=document.createElement('ul');for(const sample of report.recent){const item=document.createElement('li');const replay=sample.method?.includes('CONFIRMED_SPLIT_PATTERN');const timing=sample.method?.startsWith('CONFIRM_TIME_')?'recomputed at confirmation':'saved preview';item.textContent=`Pattern ${sample.pattern} · ${replay?'Confirmed pattern':'Rule based'} · ${timing} · ${sample.pages} pages · ${sample.correctedPages} type corrections · boundaries ${sample.boundariesChanged?'changed':'unchanged'} · ${sample.confirmedAt}`;const mapping=document.createElement("p");mapping.textContent="Proposed: "+ranges(sample.proposedParts)+" → Confirmed: "+ranges(sample.confirmedParts);item.append(mapping);list.append(item);}details.append(summary,list);output.append(details);
 }catch(error){if(current===generation){output.replaceChildren();line('Quality could not be loaded: '+error.message);}}finally{if(current===generation)refresh.disabled=false;}}
 refresh.addEventListener('click',load);new MutationObserver(()=>{if(!section.classList.contains('hidden'))load();}).observe(section,{attributes:true,attributeFilter:['class']});if(!section.classList.contains('hidden'))load();
})();
