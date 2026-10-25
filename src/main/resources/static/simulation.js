function simulationField(label,name,value,type='text'){
    return `<label>${esc(label)}<input name="${name}" type="${type}" value="${esc(value??'')}" ${type==='number'?'min="0" step="0.01"':''}></label>`;
}
async function openValidationSimulation(){
    const lc=activeLc;if(!lc)return;
    let dialog=document.querySelector('#simulationDialog');
    if(!dialog){dialog=document.createElement('dialog');dialog.id='simulationDialog';dialog.className='wide-dialog';document.body.append(dialog);}
    dialog.innerHTML='<p>'+tx('sih.0d85ec','Testpaket wird geladen …')+'</p>';dialog.showModal();
    try{
        const documents=await json(`/api/lcs/${lc.id}/documents`);
        dialog.innerHTML=`<form id="simulationForm"><div class="dialoghead"><div><h2>${tx('sih.7849a1','Validierung simulieren ·')} ${esc(lc.reference)}</h2><p>${tx('sih.243868','Nur Testwerte. Originalakte und Prüfentscheidungen bleiben unverändert. Bestehende Entscheidungen werden im Test nicht übernommen.')}</p></div><button type="button" data-simulation-close class="ghost">×</button></div><div class="form-grid">${simulationField(tx('sil.370950','LC-Betrag'),'lcAmount',lc.amount,'number')}${simulationField(tx('sil.95d34e','LC-Verfallsdatum'),'expiryDate',lc.expiryDate,'date')}${simulationField(tx('sil.a9d0ab','Letzter Versandtermin'),'latestShipmentDate',lc.latestShipmentDate,'date')}</div><small>${tx('sih.da8e9a','Leere LC-Felder behalten den Originalwert. Leere Dokumentfelder werden im Test als fehlend behandelt. Dokumenttext und extrahierte Werte bleiben als Vergleichsgrundlage erhalten.')}</small><h3>${tx('sih.1acdb8','Dokumente im Testpaket')}</h3>${documents.map((d,i)=>`<fieldset data-simulation-document="${i}"><legend>${esc(d.originalFilename)}</legend><div class="form-grid">${simulationField(tx('sil.8d2ca8','Dokumentdatum'),'documentDate',d.documentDate,'date')}${simulationField(tx('sil.000374','Betrag'),'amount',d.amount,'number')}${simulationField(tx('sil.962439','Währung (z. B. EUR)'),'currency',d.currency)}</div></fieldset>`).join('')||'<p>'+tx('sih.d486ea','Keine Dokumente in dieser Akte.')+'</p>'}<div class="error" id="simulationError" role="alert"></div><div class="actions"><button type="button" data-simulation-close class="secondary">${tx('sih.8311b9','Schließen')}</button><button type="submit">${tx('sih.989c58','Testlauf starten')}</button></div></form><section id="simulationResults" aria-live="polite"></section>`;
        dialog.querySelectorAll('[data-simulation-close]').forEach(b=>b.onclick=()=>dialog.close());
        dialog.querySelector('form').onsubmit=async event=>{
            event.preventDefault();const form=event.target,button=form.querySelector('[type="submit"]');button.disabled=true;
            dialog.querySelector('#simulationError').textContent='';
            const amount=value=>value===''?null:Number(value);
            const payload={amount:amount(form.elements.lcAmount.value),expiryDate:form.elements.expiryDate.value||null,latestShipmentDate:form.elements.latestShipmentDate.value||null,documents:documents.map((d,i)=>{const row=form.querySelector(`[data-simulation-document="${i}"]`);return {id:d.id,documentDate:row.querySelector('[name="documentDate"]').value||null,amount:amount(row.querySelector('[name="amount"]').value),currency:row.querySelector('[name="currency"]').value.trim().toUpperCase()||null};})};
            try{
                const review=await json(`/api/lcs/${lc.id}/document-checks/simulation`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
                dialog.querySelector('#simulationResults').innerHTML=`<h3>${tx('sih.dfdd45','Simulationsergebnis – nicht gespeichert')}</h3><p>${tx('six.summary','{d} Abweichungen · {w} Hinweise · {p} erfüllt',{d:review.discrepancies,w:review.warnings,p:review.passed})}</p>${review.results.map(r=>`<article class="card"><b>${esc(r.severity)} · ${esc(r.message)}</b><p>${tx('sih.315ecb','LC-Bedingung:')} ${esc(r.lcCondition||'–')}</p><p>${tx('sih.61f8ce','Dokument:')} ${esc(r.documentName||'–')} · ${esc((globalThis.readableEvidence?readableEvidence(r.documentEvidence):r.documentEvidence)||'–')}</p><small>${tx('sih.072b79','Prüfregel:')} ${esc(r.code)}</small>${renderRuleMetadata(r)}</article>`).join('')}`;
            }catch(error){dialog.querySelector('#simulationError').textContent=error.message;}finally{button.disabled=false;}
        };
    }catch(error){dialog.innerHTML=`<p class="error">${esc(error.message)}</p><button type="button" data-simulation-close>${tx('sih.8311b9','Schließen')}</button>`;dialog.querySelector('button').onclick=()=>dialog.close();}
}
document.addEventListener('click',event=>{if(event.target.closest('[data-simulation-open]'))openValidationSimulation();});
