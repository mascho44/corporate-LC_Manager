function simulationField(label,name,value,type='text'){
    return `<label>${esc(label)}<input name="${name}" type="${type}" value="${esc(value??'')}" ${type==='number'?'min="0" step="0.01"':''}></label>`;
}
async function openValidationSimulation(){
    const lc=activeLc;if(!lc)return;
    let dialog=document.querySelector('#simulationDialog');
    if(!dialog){dialog=document.createElement('dialog');dialog.id='simulationDialog';dialog.className='wide-dialog';document.body.append(dialog);}
    dialog.innerHTML='<p>Testpaket wird geladen …</p>';dialog.showModal();
    try{
        const documents=await json(`/api/lcs/${lc.id}/documents`);
        dialog.innerHTML=`<form id="simulationForm"><div class="dialoghead"><div><h2>Validierung simulieren · ${esc(lc.reference)}</h2><p>Nur Testwerte. Originalakte und Prüfentscheidungen bleiben unverändert. Bestehende Entscheidungen werden im Test nicht übernommen.</p></div><button type="button" data-simulation-close class="ghost">×</button></div><div class="form-grid">${simulationField('LC-Betrag','lcAmount',lc.amount,'number')}${simulationField('LC-Verfallsdatum','expiryDate',lc.expiryDate,'date')}${simulationField('Letzter Versandtermin','latestShipmentDate',lc.latestShipmentDate,'date')}</div><small>Leere LC-Felder behalten den Originalwert. Leere Dokumentfelder werden im Test als fehlend behandelt. Dokumenttext und extrahierte Werte bleiben als Vergleichsgrundlage erhalten.</small><h3>Dokumente im Testpaket</h3>${documents.map((d,i)=>`<fieldset data-simulation-document="${i}"><legend>${esc(d.originalFilename)}</legend><div class="form-grid">${simulationField('Dokumentdatum','documentDate',d.documentDate,'date')}${simulationField('Betrag','amount',d.amount,'number')}${simulationField('Währung (z. B. EUR)','currency',d.currency)}</div></fieldset>`).join('')||'<p>Keine Dokumente in dieser Akte.</p>'}<div class="error" id="simulationError" role="alert"></div><div class="actions"><button type="button" data-simulation-close class="secondary">Schließen</button><button type="submit">Testlauf starten</button></div></form><section id="simulationResults" aria-live="polite"></section>`;
        dialog.querySelectorAll('[data-simulation-close]').forEach(b=>b.onclick=()=>dialog.close());
        dialog.querySelector('form').onsubmit=async event=>{
            event.preventDefault();const form=event.target,button=form.querySelector('[type="submit"]');button.disabled=true;
            dialog.querySelector('#simulationError').textContent='';
            const amount=value=>value===''?null:Number(value);
            const payload={amount:amount(form.elements.lcAmount.value),expiryDate:form.elements.expiryDate.value||null,latestShipmentDate:form.elements.latestShipmentDate.value||null,documents:documents.map((d,i)=>{const row=form.querySelector(`[data-simulation-document="${i}"]`);return {id:d.id,documentDate:row.querySelector('[name="documentDate"]').value||null,amount:amount(row.querySelector('[name="amount"]').value),currency:row.querySelector('[name="currency"]').value.trim().toUpperCase()||null};})};
            try{
                const review=await json(`/api/lcs/${lc.id}/document-checks/simulation`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
                dialog.querySelector('#simulationResults').innerHTML=`<h3>Simulationsergebnis – nicht gespeichert</h3><p>${review.discrepancies} Abweichungen · ${review.warnings} Hinweise · ${review.passed} erfüllt</p>${review.results.map(r=>`<article class="card"><b>${esc(r.severity)} · ${esc(r.message)}</b><p>LC-Bedingung: ${esc(r.lcCondition||'–')}</p><p>Dokument: ${esc(r.documentName||'–')} · ${esc(r.documentEvidence||'–')}</p><small>Prüfregel: ${esc(r.code)}</small></article>`).join('')}`;
            }catch(error){dialog.querySelector('#simulationError').textContent=error.message;}finally{button.disabled=false;}
        };
    }catch(error){dialog.innerHTML=`<p class="error">${esc(error.message)}</p><button type="button" data-simulation-close>Schließen</button>`;dialog.querySelector('button').onclick=()=>dialog.close();}
}
document.addEventListener('click',event=>{if(event.target.closest('[data-simulation-open]'))openValidationSimulation();});
