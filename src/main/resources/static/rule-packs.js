(() => {
 const api='/api/settings/rule-packs',get=id=>document.getElementById(id);
 const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 let csrf='',source=null,preview=null,sequence=0,busy=false;
 async function request(url,options={}){
  const headers={...(options.headers||{})};
  if(options.method&&options.method!=='GET'){headers['X-CSRF-TOKEN']=csrf;headers['Content-Type']='application/json';}
  const response=await fetch(url,{...options,headers});
  if(response.status===401){location.assign('/login.html');throw Error(tx('rpl.5a77c6','Anmeldung erforderlich.'));}
  if(!response.ok){let message='HTTP '+response.status;try{message=(await response.json()).error||message;}catch{}throw Error(message);}
  return response.status===204||response.headers.get('content-length')==='0'?null:await response.text().then(text=>text?JSON.parse(text):null);
 }
 function message(text){get('packMessage').textContent=text;}
 function renderPreview(result){
  const p=result.definition;
  get('packPreview').innerHTML='<h3>'+escape(p.name)+' · '+escape(p.version)+'</h3><p>'+escape(p.packId)+' · '+escape(p.license)+'</p><p>'+escape(p.rightsStatement)+'</p><p class="pack-checksum">SHA-256 '+escape(result.checksum)+'</p><p>'+p.rules.length+tx('rpl.52ace0',' Regeln · Tests ')+(result.testsPassed?'bestanden':'fehlgeschlagen – Aktivierung gesperrt')+'</p><details><summary>'+tx('rph.cf1c30','Regeln ansehen')+'</summary><ul>'+p.rules.map(r=>'<li>'+escape(r.id+' v'+r.version+' · '+r.documentType+': '+r.left+' '+r.operator+' '+r.right)+'<br>'+escape((r.mode||'AUTOMATIC')+' · '+(r.conditions||[]).map(c=>c.field+' '+c.operator+' '+c.value).join(' UND '))+'<br>'+escape(r.message)+'<br><small>'+escape(r.sourceReference)+'</small></li>').join('')+'</ul></details><div class="pack-table-wrap"><table class="pack-table"><thead><tr><th>'+tx('rph.0cbc66','Test')+'</th><th>'+tx('rph.49dc0b','Erwartet')+'</th><th>'+tx('rph.cbdbdb','Ergebnis')+'</th></tr></thead><tbody>'+result.tests.map(t=>'<tr><td>'+escape(t.name)+'</td><td>'+escape(t.expected)+'</td><td>'+escape(t.actual)+' · '+(t.passed?'OK':'FEHLER')+'</td></tr>').join('')+'</tbody></table></div>';
 }
 async function refresh(){
  const rows=await request(api);
  get('packVersions').innerHTML=rows.length?'<div class="pack-table-wrap"><table class="pack-table"><thead><tr><th>'+tx('rph.0b927e','Pack / Version')+'</th><th>'+tx('rph.ec53a8','Status')+'</th><th>'+tx('rph.872073','Aktionen')+'</th></tr></thead><tbody>'+rows.map(p=>'<tr><td><b>'+escape(p.name)+'</b><small>'+escape(p.packId+' v'+p.version)+'</small><small>'+escape(p.importedBy+' · '+p.importedAt)+'</small><details><summary>'+tx('rph.efd646','Prüfsumme')+'</summary><span class="pack-checksum">'+escape(p.checksum)+'</span></details></td><td>'+(p.active?'Aktiv':p.previous?'Vorherige Version':tx('rpl.2aaad1','Inaktiv'))+'<small>'+tx('rph.90792d','Tests')+' '+(p.testsPassed?'bestanden':'fehlgeschlagen')+'</small></td><td><button class="secondary" data-test="'+p.id+'">'+tx('rph.2472ad','Testlauf')+'</button>'+(p.active?'<button class="secondary" data-deactivate="'+escape(p.packId)+'">'+tx('rph.5e3e07','Deaktivieren')+'</button>':'<button data-activate="'+p.id+'" data-label="'+escape(p.packId+' v'+p.version)+'" '+(p.testsPassed?'':'disabled')+'>'+(p.previous?'Vorherige Version aktivieren':tx('rpl.2958e4','Aktivieren'))+'</button>')+'<button class="danger" data-delete="'+p.id+'" data-label="'+escape(p.packId+' v'+p.version)+'" '+(p.active?'disabled title="'+tx('rph.2ce554','Zuerst deaktivieren')+'"':'')+'>'+tx('rph.1010b0','Löschen')+'</button></td></tr>').join('')+'</tbody></table></div>':tx('rpl.0281ff','Noch keine Packs importiert.');
 }
 get('packFile').onchange=async()=>{
  const current=++sequence;source=null;preview=null;get('packImport').disabled=true;message('');
  const file=get('packFile').files[0];if(!file)return;
  if(file.size>5*1024*1024){message(tx('rpl.a4f565','Datei überschreitet 5 MB.'));return;}
  get('packPreview').textContent=tx('rpl.243d93','Datei wird validiert und getestet …');
  try{const text=await file.text();const result=await request(api+'/preview',{method:'POST',body:text});
   if(current!==sequence)return;
   if(result.kind==='SPECIFICATION'){
    get('packPreview').innerHTML='<h3>'+tx('rph.f02a2c','Erweiterungsspezifikation · Kompatibilität')+'</h3><p>'+result.supported+tx('rpl.c8a06e',' von ')+result.rules+tx('rpl.923709',' Regeln strukturell unterstützt · Schema ')+result.schemaVersion+'</p><p>'+escape(result.message)+'</p><ul>'+[...(result.configurationRequirements||[]).map(reason=>({ruleId:tx('rpl.704183','Konfiguration'),reason})),...result.issues].map(i=>'<li>'+escape(i.ruleId)+': '+escape(i.reason)+'</li>').join('')+'</ul>';
    message(tx('rpl.9de7ca','Spezifikation geprüft, nicht gespeichert oder aktiviert.'));return;
   }
   source=text;preview=result;renderPreview(result);get('packImport').disabled=false;
  }catch(error){if(current===sequence){message(error.message);get('packPreview').textContent=tx('rpl.77aec1','Keine gültige Vorschau.');}}
 };
 get('packImport').onclick=async()=>{
  if(!source||!preview||busy)return;busy=true;get('packImport').disabled=true;get('packFile').disabled=true;
  try{await request(api,{method:'POST',body:source});message(tx('rpl.9290d8','Version inaktiv gespeichert. Es wurde keine Regel aktiviert.'));source=null;preview=null;await refresh();}
  catch(error){message(error.message);get('packImport').disabled=false;}
  finally{busy=false;get('packFile').disabled=false;}
 };
 get('packRefresh').onclick=()=>refresh().catch(error=>message(error.message));
 get('packVersions').onclick=async event=>{
  const button=event.target.closest('button');if(!button||busy)return;
  if(button.dataset.activate){
   if(!get('packRights').checked){message(tx('rpl.428217','Bitte zuerst Nutzungsrechte und interne Freigabe bestätigen.'));return;}
   if(!confirm(button.dataset.label+tx('rpl.06c00f',' für alle LC-Akten aktivieren?')))return;
  }
  if(button.dataset.deactivate&&!confirm(tx('rpl.9634dc','Pack ')+button.dataset.deactivate+tx('rpl.217524',' für alle LC-Akten deaktivieren?')))return;
  if(button.dataset.delete&&!confirm(button.dataset.label+tx('rpl.f69017',' endgültig löschen? Audit-Protokolle und bisherige Befunde bleiben erhalten.')))return;
  busy=true;button.disabled=true;message('');
  try{
   if(button.dataset.test){++sequence;source=null;preview=null;get('packImport').disabled=true;get('packFile').disabled=true;renderPreview(await request(api+'/'+button.dataset.test+'/test',{method:'POST'}));}
   else if(button.dataset.activate){await request(api+'/'+button.dataset.activate+'/activate',{method:'POST',body:JSON.stringify({rightsConfirmed:true})});get('packRights').checked=false;await refresh();message('Pack-Version aktiviert.');}
   else if(button.dataset.deactivate){await request(api+'/'+encodeURIComponent(button.dataset.deactivate)+'/deactivate',{method:'POST'});await refresh();message('Pack deaktiviert.');}
   else if(button.dataset.delete){await request(api+'/'+button.dataset.delete,{method:'DELETE'});get('packPreview').textContent='';await refresh();message('Pack-Version gelöscht.');}
  }catch(error){message(error.message);}
  finally{busy=false;button.disabled=false;get('packFile').disabled=false;}
 };
 const ruleSourceApi='/api/settings/rule-source';
 async function loadRuleSource(){
  const current=await request(ruleSourceApi);get('ruleSourceMode').value=current.mode;
  get('ruleSourceMode').disabled=false;get('ruleSourceSave').disabled=false;
 }
 get('ruleSourceSave').onclick=async()=>{
  const button=get('ruleSourceSave');button.disabled=true;
  try{const saved=await request(ruleSourceApi,{method:'PUT',body:JSON.stringify({mode:get('ruleSourceMode').value})});get('ruleSourceMode').value=saved.mode;message(tx('rpl.465ef7','Regelquelle gespeichert.'));}
  catch(error){message(error.message);}
  finally{button.disabled=false;}
 };
 (async()=>{
  try{const user=await request('/api/auth/me');csrf=user.csrfToken;
   if(!user.permissions?.includes('SETTINGS_MANAGE'))throw Error(tx('rpl.7dbd3b','Für Rule Packs wird das Recht SETTINGS_MANAGE benötigt.'));
   get('packFile').disabled=false;get('packRefresh').disabled=false;await refresh();await loadRuleSource();
  }catch(error){message(error.message);get('packVersions').textContent=tx('rpl.cc6042','Verwaltung nicht verfügbar.');}
 })();
})();
