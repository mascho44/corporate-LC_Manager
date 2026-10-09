(()=>{'use strict';
 const section=document.getElementById('trainingSection');if(!section)return;
 const panel=document.createElement('div');panel.className='module-actions';
 const heading=document.createElement('h3');heading.textContent='Qualität der Aufteilung';
 const note=document.createElement('p');note.textContent='Beobachtete Korrekturen in diesem Arbeitsbereich (letzte 1.000 gemessene Bestätigungen). Frühere Bestätigungen ohne gespeicherten Vorschlag werden nicht bewertet. Das Wiedererkennen bestätigter Muster ist kein allgemeines KI-Lernen und kein Beleg für die Richtigkeit von Dokumenten. Änderungen an Original/Copy sind ausgenommen.';
 const refresh=document.createElement('button');refresh.type='button';refresh.textContent='Qualität aktualisieren';
 const output=document.createElement('div');output.setAttribute('role','status');panel.append(heading,note,refresh,output);section.append(panel);
 let generation=0;
 const line=value=>{const p=document.createElement('p');p.textContent=value;output.append(p);};
 const ranges=parts=>(parts||[]).map(part=>`${part.fromPage}–${part.toPage}: ${part.documentType.replaceAll('_',' ').toLowerCase()}`).join('; ');
 async function load(){const current=++generation;refresh.disabled=true;output.replaceChildren();line('Wird geladen …');try{
  const response=await fetch('/api/training/document-types/quality',{cache:'no-store'});if(!response.ok)throw new Error('HTTP '+response.status);const report=await response.json();if(current!==generation)return;
  output.replaceChildren();line(`${report.confirmations} Bestätigungen · ${report.measured} gemessen · ${report.pages} geprüfte Seiten`);
  if(!report.measured){line('Noch keine gemessenen Bestätigungen.');return;}
  line(`${report.correctedPages} Seiten mit korrigiertem Dokumenttyp · ${report.changedBoundaries} Bestätigungen mit geänderten Grenzen`);
  const details=document.createElement('details');const summary=document.createElement('summary');summary.textContent='Letzte Bestätigungen';const list=document.createElement('ul');for(const sample of report.recent){const item=document.createElement('li');const replay=sample.method?.includes('CONFIRMED_SPLIT_PATTERN');const timing=sample.method?.startsWith('CONFIRM_TIME_')?'bei Bestätigung neu berechnet':'gespeicherter Vorschlag';item.textContent=`Muster ${sample.pattern} · ${replay?'Bestätigtes Muster':'Regelbasiert'} · ${timing} · ${sample.pages} Seiten · ${sample.correctedPages} Typkorrekturen · Grenzen ${sample.boundariesChanged?'geändert':'unverändert'} · ${sample.confirmedAt}`;const mapping=document.createElement("p");mapping.textContent="Vorschlag: "+ranges(sample.proposedParts)+" → Bestätigt: "+ranges(sample.confirmedParts);item.append(mapping);list.append(item);}details.append(summary,list);output.append(details);
 }catch(error){if(current===generation){output.replaceChildren();line('Qualität konnte nicht geladen werden: '+error.message);}}finally{if(current===generation)refresh.disabled=false;}}
 refresh.addEventListener('click',load);new MutationObserver(()=>{if(!section.classList.contains('hidden'))load();}).observe(section,{attributes:true,attributeFilter:['class']});if(!section.classList.contains('hidden'))load();
 const benchmark=document.createElement('button');benchmark.type='button';benchmark.textContent='Synthetischen Erkennungstest ausführen';
 const benchmarkOutput=document.createElement('div');benchmarkOutput.setAttribute('role','status');panel.append(benchmark,benchmarkOutput);
 benchmark.addEventListener('click',async()=>{
  benchmark.disabled=true;benchmarkOutput.textContent='Beschriftete synthetische Tests laufen …';
  try{
   const response=await fetch('/api/training/document-types/benchmark',{cache:'no-store'});if(!response.ok)throw new Error('HTTP '+response.status);
   const report=await response.json();benchmarkOutput.replaceChildren();
   const add=text=>{const row=document.createElement('p');row.textContent=text;benchmarkOutput.append(row);};
   const percent=value=>value==null?'nicht gemessen':Math.round(value*100)+'%';
   add('Synthetische reine Textbeispiele – keine echte Scan-Genauigkeit. Trainingsbeispiele des Mandanten werden nicht verwendet.');
   add(report.corpusVersion+' · '+report.examples+' Fälle · '+report.correctTypes+'/'+report.pages+' Seiten mit richtigem Typ');
   add('Genauigkeit der Grenzen: '+percent(report.boundaries.precision)+' · Trefferquote: '+percent(report.boundaries.recall));
   for(const [type,value] of Object.entries(report.types))add(type+': '+value.correct+'/'+value.positiveLabels+' richtig · '+value.falsePositives+' Fehlalarme');
   for(const [field,value] of Object.entries(report.metadata))add(field+': '+value.correct+'/'+value.positiveLabels+' beschriftete Werte richtig · '+value.missed+' übersehen · '+value.falsePositives+' Fehlalarme · '+value.correctNegatives+'/'+value.negativeLabels+' Negativfälle richtig');
   add('Korpus-SHA-256: '+report.corpusSha256);
   add('Engine-SHA-256: '+report.engineSha256);
   const download=document.createElement('a');download.href='/api/training/document-types/benchmark';download.download='recognition-benchmark.json';download.textContent='Testergebnis als JSON herunterladen';benchmarkOutput.append(download);
   for(const result of report.cases.filter(result=>!result.exactSplit))add('Beispiel prüfen: '+result.id+' – Aufteilung weicht von der Beschriftung ab');
  }catch(error){benchmarkOutput.textContent='Test konnte nicht ausgeführt werden: '+error.message;}
  finally{benchmark.disabled=false;}
 });
})();
