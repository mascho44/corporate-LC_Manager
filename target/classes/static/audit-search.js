/* Audit-Dialog: Zeitraum, Benutzer und Aktion auf dem Server filtern (bis 2000 Treffer), gefilterter CSV-Export, Aufbewahrungshinweis. */
(()=>{'use strict';
 const toolbar=document.querySelector('#auditDialog .audit-toolbar');if(!toolbar)return;
 const make=(tag,props={})=>Object.assign(document.createElement(tag),props);
 const from=make('input',{type:'date'}),to=make('input',{type:'date'}),user=make('input',{type:'search',placeholder:'Benutzer (Server)'});
 from.setAttribute('aria-label','Von');to.setAttribute('aria-label','Bis');user.setAttribute('aria-label','Benutzer');
 const load=make('button',{type:'button',className:'secondary',textContent:'Zeitraum laden'});
 const exportButton=make('button',{type:'button',className:'secondary',textContent:'CSV (Filter)'});
 const status=make('div',{className:'audit-retention'});status.setAttribute('role','status');
 toolbar.append(from,to,user,load,exportButton);toolbar.after(status);
 const query=()=>{const p=new URLSearchParams();if(from.value)p.set('from',from.value);if(to.value)p.set('to',to.value);if(user.value.trim())p.set('user',user.value.trim());return p;};
 load.addEventListener('click',async()=>{
  load.disabled=true;
  try{
   const p=query();p.set('limit','2000');
   const response=await fetch('/api/audit?'+p,{cache:'no-store'});
   if(!response.ok)throw new Error((await response.json().catch(()=>({}))).error||'HTTP '+response.status);
   globalThis.auditEvents=await response.json();if(typeof filterAudit==='function')filterAudit();
  }catch(error){status.textContent='Laden nicht möglich: '+error.message;}
  finally{load.disabled=false;}
 });
 exportButton.addEventListener('click',()=>{const q=query().toString();location.assign('/api/audit/export.csv'+(q?'?'+q:''));});
 async function retention(){
  try{
   const response=await fetch('/api/audit/retention',{cache:'no-store'});if(!response.ok)return;
   const r=await response.json();
   const oldest=r.oldestEvent?new Date(r.oldestEvent).toLocaleDateString('de-DE'):'–';
   status.textContent=`Aufbewahrung: ${r.retentionYears} Jahre · ältester Eintrag ${oldest} · ${r.eventsBeyondRetention} Einträge außerhalb der Frist · keine automatische Löschung.`;
  }catch{}
 }
 document.querySelector('#auditBtn')?.addEventListener('click',retention);
})();
