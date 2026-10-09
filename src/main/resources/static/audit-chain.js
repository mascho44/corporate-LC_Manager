/* "Kette prüfen" im Audit-Dialog: ruft die Hash-Ketten-Prüfung des Mandanten auf und zeigt Ergebnis, Kopf-Hash und Anzahl nicht verketteter Altdaten. */
(()=>{'use strict';
 const toolbar=document.querySelector('#auditDialog .audit-toolbar');if(!toolbar)return;
 const button=document.createElement('button');button.type='button';button.className='secondary';button.textContent='Kette prüfen';
 const result=document.createElement('div');result.setAttribute('role','status');result.className='audit-chain-result';
 toolbar.append(button);toolbar.after(result);
 const line=(text,className)=>{const p=document.createElement('p');p.textContent=text;if(className)p.className=className;result.append(p);};
 button.addEventListener('click',async()=>{
  button.disabled=true;result.replaceChildren();line('Kette wird geprüft …');
  try{
   const response=await fetch('/api/audit/chain',{cache:'no-store'});if(!response.ok)throw new Error('HTTP '+response.status);
   const chain=await response.json();result.replaceChildren();
   if(chain.ok){
    line(`Kette intakt: ${chain.checked} Einträge geprüft${chain.unchained?`, ${chain.unchained} ältere Einträge sind nicht verkettet`:''}.`,'success');
    if(chain.headHash)line(`Kopf: Nr. ${chain.headSeq} · ${chain.headHash}`);
   }else{
    line(`Kette gebrochen bei Nr. ${chain.firstBadSeq}: ${chain.reason||'Abweichung'}. ${chain.checked} Einträge bis dahin geprüft.`,'error');
   }
  }catch(error){result.replaceChildren();line('Prüfung nicht möglich: '+error.message,'error');}
  finally{button.disabled=false;}
 });
})();
