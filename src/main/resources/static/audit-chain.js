/* "Kette prüfen" im Audit-Dialog: ruft die Hash-Ketten-Prüfung des Mandanten auf und zeigt Ergebnis, Kopf-Hash und Anzahl nicht verketteter Altdaten. */
(()=>{'use strict';
 const toolbar=document.querySelector('#auditDialog .audit-toolbar');if(!toolbar)return;
 const button=document.createElement('button');button.type='button';button.className='secondary';button.textContent=tx('acl.47122d','Kette prüfen');
 const result=document.createElement('div');result.setAttribute('role','status');result.className='audit-chain-result';
 toolbar.append(button);toolbar.after(result);
 const line=(text,className)=>{const p=document.createElement('p');p.textContent=text;if(className)p.className=className;result.append(p);};
 button.addEventListener('click',async()=>{
  button.disabled=true;result.replaceChildren();line(tx('acl.700ad4','Kette wird geprüft …'));
  try{
   const response=await fetch('/api/audit/chain',{cache:'no-store'});if(!response.ok)throw new Error('HTTP '+response.status);
   const chain=await response.json();result.replaceChildren();
   if(chain.ok){
    line(tx('acx.ok','Kette intakt: {n} Einträge geprüft{extra}.',{n:chain.checked,extra:chain.unchained?tx('acx.unchained',', {n} ältere Einträge sind nicht verkettet',{n:chain.unchained}):''}),'success');
    if(chain.headHash)line(`Kopf: Nr. ${chain.headSeq} · ${chain.headHash}`);
   }else{
    line(tx('acx.broken','Kette gebrochen bei Nr. {seq}: {reason}. {n} Einträge bis dahin geprüft.',{seq:chain.firstBadSeq,reason:chain.reason||tx('acl.ef408c','Abweichung'),n:chain.checked}),'error');
   }
  }catch(error){result.replaceChildren();line(tx('acl.f04ed0','Prüfung nicht möglich: ')+error.message,'error');}
  finally{button.disabled=false;}
 });
})();
