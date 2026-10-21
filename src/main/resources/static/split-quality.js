(()=>{'use strict';
 const section=document.getElementById('trainingSection');if(!section)return;
 const panel=document.createElement('div');panel.className='module-actions';
 const heading=document.createElement('h3');heading.textContent='Qualität der Aufteilung';
 const note=document.createElement('p');note.textContent='Beobachtete Korrekturen in diesem Arbeitsbereich (letzte 1.000 gemessene Bestätigungen). Frühere Bestätigungen ohne gespeicherten Vorschlag werden nicht bewertet. Das Wiedererkennen bestätigter Muster ist kein allgemeines KI-Lernen und kein Beleg für die Richtigkeit von Dokumenten. Änderungen an Original/Copy sind ausgenommen.';
 const refresh=document.createElement('button');refresh.type='button';refresh.textContent=tx('sql.dd272c','Qualität aktualisieren');
 const output=document.createElement('div');output.setAttribute('role','status');panel.append(heading,note,refresh,output);section.append(panel);
 let generation=0;
 const line=value=>{const p=document.createElement('p');p.textContent=value;output.append(p);};
 const ranges=parts=>(parts||[]).map(part=>`${part.fromPage}–${part.toPage}: ${part.documentType.replaceAll('_',' ').toLowerCase()}`).join('; ');
 async function load(){const current=++generation;refresh.disabled=true;output.replaceChildren();line(tx('sql.cd73c1','Wird geladen …'));try{
  const response=await fetch('/api/training/document-types/quality',{cache:'no-store'});if(!response.ok)throw new Error(tx('sql.995c94','HTTP ')+response.status);const report=await response.json();if(current!==generation)return;
  output.replaceChildren();line(tx('sqt.4b1c53','{p0} Bestätigungen · {p1} gemessen · {p2} geprüfte Seiten',{p0:report.confirmations,p1:report.measured,p2:report.pages}));
  if(!report.measured){line(tx('sql.49a476','Noch keine gemessenen Bestätigungen.'));return;}
  line(tx('sqt.0b89cd','{p0} Seiten mit korrigiertem Dokumenttyp · {p1} Bestätigungen mit geänderten Grenzen',{p0:report.correctedPages,p1:report.changedBoundaries}));
  const details=document.createElement('details');const summary=document.createElement('summary');summary.textContent=tx('sql.35cba4','Letzte Bestätigungen');const list=document.createElement('ul');for(const sample of report.recent){const item=document.createElement('li');const replay=sample.method?.includes('CONFIRMED_SPLIT_PATTERN');const timing=sample.method?.startsWith('CONFIRM_TIME_')?tx('sqf.0','bei Bestätigung neu berechnet'):'gespeicherter Vorschlag';item.textContent=`Muster ${sample.pattern} · ${replay?tx('sql.7313c0','Bestätigtes Muster'):tx('sql.1aa610','Regelbasiert')} · ${timing} · ${sample.pages} Seiten · ${sample.correctedPages} Typkorrekturen · Grenzen ${sample.boundariesChanged?'geändert':'unverändert'} · ${sample.confirmedAt}`;const mapping=document.createElement("p");mapping.textContent="Vorschlag: "+ranges(sample.proposedParts)+" → Bestätigt: "+ranges(sample.confirmedParts);item.append(mapping);list.append(item);}details.append(summary,list);output.append(details);
 }catch(error){if(current===generation){output.replaceChildren();line(tx('sql.4aa90c','Qualität konnte nicht geladen werden: ')+error.message);}}finally{if(current===generation)refresh.disabled=false;}}
 refresh.addEventListener('click',()=>{loadAccuracy();return load();});new MutationObserver(()=>{if(!section.classList.contains('hidden'))load();}).observe(section,{attributes:true,attributeFilter:['class']});if(!section.classList.contains('hidden'))load();
 // Hit rate per document type: first automatic suggestion compared with the type a person confirmed.
 const accuracyHeading=document.createElement('h3');accuracyHeading.textContent='Trefferquote je Dokumentart';
 const accuracyNote=document.createElement('p');accuracyNote.textContent='Erster automatischer Vorschlag im Vergleich zur bestätigten Art, über Dokumente in Akten und bestätigte Posteingangs-Dokumente. Beobachtete Werte, keine statistische Garantie.';
 const accuracyOutput=document.createElement('div');accuracyOutput.className='accuracy-report';accuracyOutput.setAttribute('role','status');
 panel.append(accuracyHeading,accuracyNote,accuracyOutput);
 const pct=value=>value==null?'–':Math.round(value*100)+' %';
 async function loadAccuracy(){
  accuracyOutput.textContent=tx('sql.cd73c1','Wird geladen …');
  try{
   const response=await fetch('/api/training/document-types/accuracy',{cache:'no-store'});if(!response.ok)throw new Error(tx('sql.995c94','HTTP ')+response.status);
   const report=await response.json();accuracyOutput.replaceChildren();
   const sum=document.createElement('p');sum.textContent=report.documents?tx('sqg.e9fb1f','{p0} von {p1} Dokumenten richtig erkannt ({p2})',{p0:report.correct,p1:report.documents,p2:pct(report.rate)}):tx('sqf.2','Noch keine bestätigten Dokumente zum Vergleich.');accuracyOutput.append(sum);
   if(!report.documents)return;
   const table=document.createElement('table');table.className='pu-table';
   const head=document.createElement('tr');['Dokumentart',tx('sql.ad89b7','Dokumente'),'richtig',tx('sql.c48e92','Quote'),'falsch','ohne Vorschlag','davon sicher richtig'].forEach(h=>{const th=document.createElement('th');th.textContent=h;head.append(th);});table.append(head);
   report.types.forEach(row=>{const tr=document.createElement('tr');[row.label,row.documents,row.correct,pct(row.rate),row.wrong,row.none,row.safe?tx('sqg.7552d6','{p0} von {p1}',{p0:row.correctSafe,p1:row.safe}):'–'].forEach(v=>{const td=document.createElement('td');td.textContent=String(v);tr.append(td);});if(row.rate!=null&&row.rate<.7)tr.className='accuracy-low';table.append(tr);});
   accuracyOutput.append(table);
   if(report.mistakes.length){const p=document.createElement('p');p.textContent='Häufigste Verwechslungen: '+report.mistakes.map(m=>`${m.suggested} statt ${m.confirmed} (${m.count}×)`).join(', ');accuracyOutput.append(p);}
  }catch(error){accuracyOutput.textContent=tx('sql.8b4444','Trefferquote nicht verfügbar: ')+error.message;}
 }
 new MutationObserver(()=>{if(!section.classList.contains('hidden'))loadAccuracy();}).observe(section,{attributes:true,attributeFilter:['class']});if(!section.classList.contains('hidden'))loadAccuracy();
 const benchmark=document.createElement('button');benchmark.type='button';benchmark.textContent=tx('sql.ca4580','Synthetischen Erkennungstest ausführen');
 const benchmarkOutput=document.createElement('div');benchmarkOutput.setAttribute('role','status');panel.append(benchmark,benchmarkOutput);
 benchmark.addEventListener('click',async()=>{
  benchmark.disabled=true;benchmarkOutput.textContent=tx('sql.772dc3','Beschriftete synthetische Tests laufen …');
  try{
   const response=await fetch('/api/training/document-types/benchmark',{cache:'no-store'});if(!response.ok)throw new Error(tx('sql.995c94','HTTP ')+response.status);
   const report=await response.json();benchmarkOutput.replaceChildren();
   const add=text=>{const row=document.createElement('p');row.textContent=text;benchmarkOutput.append(row);};
   const percent=value=>value==null?tx('sqf.1','nicht gemessen'):Math.round(value*100)+'%';
   add(tx('sql.02b57b','Synthetische reine Textbeispiele – keine echte Scan-Genauigkeit. Trainingsbeispiele des Mandanten werden nicht verwendet.'));
   add(report.corpusVersion+' · '+report.examples+tx('sql.46e533',' Fälle · ')+report.correctTypes+'/'+report.pages+' Seiten mit richtigem Typ');
   add(tx('sql.5e146e','Genauigkeit der Grenzen: ')+percent(report.boundaries.precision)+tx('sql.2f0c63',' · Trefferquote: ')+percent(report.boundaries.recall));
   for(const [type,value] of Object.entries(report.types))add(type+': '+value.correct+'/'+value.positiveLabels+' richtig · '+value.falsePositives+' Fehlalarme');
   for(const [field,value] of Object.entries(report.metadata))add(field+': '+value.correct+'/'+value.positiveLabels+' beschriftete Werte richtig · '+value.missed+' übersehen · '+value.falsePositives+' Fehlalarme · '+value.correctNegatives+'/'+value.negativeLabels+' Negativfälle richtig');
   add(tx('sql.698ab7','Korpus-SHA-256: ')+report.corpusSha256);
   add(tx('sql.c15466','Engine-SHA-256: ')+report.engineSha256);
   const download=document.createElement('a');download.href='/api/training/document-types/benchmark';download.download='recognition-benchmark.json';download.textContent='Testergebnis als JSON herunterladen';benchmarkOutput.append(download);
   for(const result of report.cases.filter(result=>!result.exactSplit))add(tx('sql.40a84e','Beispiel prüfen: ')+result.id+tx('sql.e3f301',' – Aufteilung weicht von der Beschriftung ab'));
  }catch(error){benchmarkOutput.textContent=tx('sql.9c7478','Test konnte nicht ausgeführt werden: ')+error.message;}
  finally{benchmark.disabled=false;}
 });
})();
