/* EBICS bank connection: own subscriber, key setup (INI/HIA, then HPB after the bank released the subscriber). */
async function setupEbics(){
 if(typeof can!=='function'||!can('SETTINGS_MANAGE'))return;
 const node=(tag,value)=>{const n=document.createElement(tag);n.textContent=value;return n;};
 const nav=node('button','EBICS-Bankanbindung');nav.type='button';nav.id='appNavEbics';
 document.querySelector('#appNav nav').append(nav);
 const section=document.createElement('section');section.id='ebicsSection';section.className='panel hidden';
 const message=node('p','');message.setAttribute('role','status');
 const info=document.createElement('div');
 const form=document.createElement('form');form.className='form-grid';
 [['url','Bank-URL (https)','https://…/ebicsweb'],['hostId','Host-ID','EVILSBANK'],['partnerId','Partner-ID (Kunden-ID)',''],['userId','Teilnehmer-ID','']].forEach(([name,label,hint])=>{
  const wrapper=node('label',label),input=document.createElement('input');input.name=name;input.required=true;input.maxLength=name==='url'?500:35;input.placeholder=hint;
  if(name!=='url')input.pattern='[A-Za-z0-9]{1,35}';wrapper.append(input);form.append(wrapper);
 });
 const save=node('button','Verbindung speichern');save.type='submit';form.append(save);
 const actions=document.createElement('div');actions.className='actions';
 const keys=node('button','Schlüssel erzeugen und senden (INI/HIA)'),bank=node('button','Bankschlüssel abholen (HPB)'),reset=node('button','Zurücksetzen');
 [keys,bank,reset].forEach(b=>{b.type='button';b.className='secondary';actions.append(b);});
 const prints=document.createElement('pre');prints.setAttribute('aria-label','Fingerabdrücke');prints.hidden=true;
 const messagesBox=document.createElement('div');messagesBox.hidden=true;
 const fetchButton=node('button','Nachrichten abholen');fetchButton.type='button';
 const list=document.createElement('div');list.className='ebics-messages';
 const autoBox=document.createElement('div');autoBox.className='ebics-auto-fetch';
 const autoLabel=node('label','Automatisch abrufen'),autoToggle=document.createElement('input');autoToggle.type='checkbox';autoToggle.name='autoFetch';autoLabel.prepend(autoToggle);
 const intervalLabel=node('label','alle '),interval=document.createElement('select');interval.name='interval';[[5,'5 Minuten'],[15,'15 Minuten'],[30,'30 Minuten'],[60,'1 Stunde'],[240,'4 Stunden'],[1440,'24 Stunden']].forEach(([v,l])=>{const o=node('option',l);o.value=v;interval.append(o);});intervalLabel.append(interval);
 const autoSave=node('button','Übernehmen');autoSave.type='button';autoSave.className='secondary';const autoInfo=node('small','');
 autoBox.append(autoLabel,intervalLabel,autoSave,autoInfo);
 messagesBox.append(node('h3','Abgeholte Nachrichten'),autoBox,node('p','Nichts wird automatisch angelegt: Nachrichten erst ansehen, dann importieren oder verwerfen.'),fetchButton,list);
 section.append(node('h2','EBICS-Bankanbindung'),node('p','Eigener Teilnehmer für den Abruf von Akkreditivnachrichten (MT700/707/710/760). Ablauf: Verbindung speichern, Schlüssel senden, Teilnehmer bankseitig freigeben lassen (INI-Brief mit den Fingerabdrücken), danach Bankschlüssel abholen.'),message,info,form,actions,prints,messagesBox);
 document.querySelector('main').append(section);
 const labels={NEW:'Neu – Schlüssel fehlen',KEYS_SENT:'Schlüssel gesendet – wartet auf Freigabe durch die Bank',ACTIVE:'Aktiv',ERROR:'Fehler'};
 let busy=false;
 function badge(c){nav.textContent='EBICS-Bankanbindung'+(c&&c.newMessages>0?' ('+c.newMessages+')':'');}
 async function refreshBadge(){try{badge(await json('/api/ebics/connection'));}catch(ignored){}}
 function showPrints(p){prints.hidden=false;prints.textContent='Fingerabdrücke (SHA-256)\nA006 Signatur:      '+p.a005+'\nE002 Verschlüsselung: '+p.e002+'\nX002 Authentifizierung: '+p.x002;}
 async function refresh(){
  message.textContent='';
  try{
   const c=await json('/api/ebics/connection');
   info.replaceChildren(node('p','Status: '+(c.configured?(labels[c.status]||c.status):'nicht eingerichtet')+(c.lastError?' · '+c.lastError:'')));
   if(!c.encryptionConfigured)info.append(node('p','Hinweis: EBICS_ENCRYPTION_KEY ist auf dem Server nicht gesetzt – Schlüssel können noch nicht erzeugt werden.'));
   ['url','hostId','partnerId','userId'].forEach(n=>{form.elements[n].value=c[n]||'';form.elements[n].disabled=c.configured&&c.status!=='NEW'&&c.status!=='ERROR';});
   save.disabled=c.configured&&c.status!=='NEW'&&c.status!=='ERROR';
   keys.disabled=!c.configured||!c.encryptionConfigured||c.status==='KEYS_SENT'||c.status==='ACTIVE';
   bank.disabled=c.status!=='KEYS_SENT';reset.disabled=!c.configured||c.status==='NEW';
   badge(c);autoToggle.checked=Boolean(c.autoFetch);interval.value=String(c.fetchIntervalMinutes||15);
   autoInfo.textContent=c.lastFetchAt?'Zuletzt abgerufen: '+String(c.lastFetchAt).replace('T',' ').slice(0,16)+(c.lastFetchResult?' · '+c.lastFetchResult:''):'Noch kein Abruf.';
   messagesBox.hidden=!(c.status==='ACTIVE'&&typeof can==='function'&&can('SWIFT_IMPORT'));
   if(!messagesBox.hidden)await loadMessages();
   if(c.configured&&c.status!=='NEW'){try{showPrints(await json('/api/ebics/connection/fingerprints'));}catch(e){prints.hidden=true;}}else prints.hidden=true;
  }catch(error){message.textContent=error.message;}
 }
 const statusLabel={NEW:'Neu',IMPORTED:'Importiert',DISCARDED:'Verworfen'};
 async function loadMessages(){
  try{
   const rows=await json('/api/ebics/messages');list.replaceChildren();
   if(!rows.length){list.append(node('p','Noch keine Nachrichten abgeholt.'));return;}
   rows.forEach(m=>{
    const row=document.createElement('article');row.className='membership-row';
    row.append(node('b',m.messageType+' · '+(m.reference||'ohne Referenz')),node('span',' '+(statusLabel[m.status]||m.status)+' · '+String(m.receivedAt||'').replace('T',' ').slice(0,16)));
    if(m.importable){
     const view=node('button','Ansehen'),imp=node('button','Importieren'),drop=node('button','Verwerfen');
     [view,imp,drop].forEach(b=>{b.type='button';b.className='secondary';row.append(b);});
     view.onclick=async()=>{try{const p=await json('/api/ebics/messages/'+m.id+'/preview');
       message.textContent=[(p.valid?'Import möglich':'Import nicht möglich')+' · Referenz '+(p.reference||'-')+(p.currency?' · '+p.currency+' '+p.amount:''),...(p.errors||[]),...(p.warnings||[])].join(' | ');}
      catch(error){message.textContent=error.message;}};
     imp.onclick=async()=>{if(await confirmAction('Nachricht '+m.messageType+' '+(m.reference||'')+' jetzt importieren?'))run(()=>json('/api/ebics/messages/'+m.id+'/import',{method:'POST'}),'Importiert.');};
     drop.onclick=async()=>{if(await confirmAction('Nachricht verwerfen? Sie wird nicht importiert.'))run(()=>json('/api/ebics/messages/'+m.id+'/discard',{method:'POST'}),'Verworfen.');};
    }
    list.append(row);
   });
  }catch(error){list.replaceChildren(node('p',error.message));}
 }
 fetchButton.onclick=()=>run(async()=>{const r=await json('/api/ebics/messages/fetch',{method:'POST'});message.textContent='';
   return {done:r};},'');
 async function run(task,done){
  if(busy)return;busy=true;message.textContent='';
  try{const result=await task();if(result&&result.a005)showPrints(result);if(result&&result.done){const r=result.done;message.textContent=r.fetched+' neue Nachricht(en), '+r.alreadyKnown+' bereits bekannt'+(r.errors&&r.errors.length?' · Fehler: '+r.errors.join('; '):'')+'.';}else message.textContent=done;}
  catch(error){message.textContent=error.message;}
  finally{busy=false;await refreshKeepMessage();}
 }
 async function refreshKeepMessage(){const text=message.textContent;await refresh();if(text)message.textContent=text;}
 const post=(path)=>json('/api/ebics/connection'+path,{method:'POST'});
 form.onsubmit=event=>{event.preventDefault();return run(()=>json('/api/ebics/connection',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(Object.fromEntries(['url','hostId','partnerId','userId'].map(n=>[n,form.elements[n].value.trim()])))}),'Verbindung gespeichert.');};
 keys.onclick=()=>run(()=>post('/keys'),'Schlüssel erzeugt und an die Bank gesendet. Bitte den Teilnehmer bankseitig freigeben lassen.');
 bank.onclick=()=>run(()=>post('/bank-keys'),'Bankschlüssel abgeholt – die Verbindung ist aktiv.');
 reset.onclick=async()=>{if(await confirmAction('Schlüssel verwerfen und die Einrichtung neu beginnen? Der Teilnehmer muss bankseitig ggf. zurückgesetzt werden.'))run(()=>post('/reset'),'Zurückgesetzt.');};
 autoSave.onclick=()=>run(()=>json('/api/ebics/connection/auto-fetch',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({enabled:autoToggle.checked,intervalMinutes:Number(interval.value)})}),autoToggle.checked?'Automatischer Abruf eingeschaltet.':'Automatischer Abruf ausgeschaltet.');
 nav.onclick=()=>{appNavigate('ebics',nav);return refresh();};
 refreshBadge();
}
