/* Read-only preliminary check; never offers decision or approval actions. */
async function openPrecheck(){
 const id=activeLc?.id;if(!id)return;
 const dialog=document.createElement('dialog');dialog.className='wide-dialog';
 const heading=document.createElement('h2');heading.textContent='Vorprüfung';
 const notice=document.createElement('p');notice.textContent='Automatische Vorprüfung der aktuellen LC-Fassung und vorhandenen Dokumente. Keine Endprüfung oder Freigabe. Bestehende fachliche Entscheidungen werden nicht übernommen oder geändert.';
 const content=document.createElement('section');content.setAttribute('role','status');content.textContent='Vorprüfung läuft …';
 const close=document.createElement('button');close.type='button';close.textContent='Schließen';close.onclick=()=>dialog.close();
 dialog.append(heading,notice,content,close);document.body.append(dialog);dialog.addEventListener('close',()=>dialog.remove());dialog.showModal();
 try{const result=await json(`/api/lcs/${encodeURIComponent(id)}/document-checks/precheck`);if(!dialog.open)return;
 const review=result.summary;content.replaceChildren();const summary=document.createElement('p');summary.textContent=`${review.discrepancies} mögliche Abweichungen · ${review.warnings} Hinweise · ${review.passed} automatisch erfüllt · ${new Date(result.checkedAt).toLocaleString()}`;content.append(summary);
 review.results.forEach(finding=>{const row=document.createElement('article');row.className='card';const title=document.createElement('b');title.textContent=finding.message;const evidence=document.createElement('p');evidence.textContent=[finding.lcCondition,finding.documentName,finding.documentEvidence].filter(Boolean).join(' · ');row.append(title,evidence);if(finding.documentName){const button=document.createElement('button');button.type='button';button.textContent='Fundstelle ansehen';button.dataset.findingEvidence=finding.code;button.dataset.findingDocument=finding.documentName;button.dataset.findingFingerprint=finding.reviewFingerprint;button.dataset.findingMode='PRECHECK';row.append(button);}content.append(row);});
 }catch(error){content.textContent=error.message;}
}
document.addEventListener('click',event=>{if(event.target.closest('[data-precheck-open]'))openPrecheck();});
