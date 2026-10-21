/* EBICS bank connection: own subscriber, key setup (INI/HIA, then HPB after the bank released the subscriber). */
async function setupEbics(){
 if(typeof can!=='function'||!can('SETTINGS_MANAGE'))return;
 const node=(tag,value)=>{const n=document.createElement(tag);n.textContent=value;return n;};
 const nav=node('button',tx('eb.0','EBICS-Bankanbindung'));nav.type='button';nav.id='appNavEbics';
 document.querySelector('#appNav nav').append(nav);
 const section=document.createElement('section');section.id='ebicsSection';section.className='panel hidden';
 const message=node('p','');message.setAttribute('role','status');
 const info=document.createElement('div');
 const form=document.createElement('form');form.className='form-grid';
 [['url',tx('eb.1','Bank-URL (https)'),'https://…/ebicsweb'],['hostId','Host-ID','EVILSBANK'],['partnerId','Partner-ID (Kunden-ID)',''],['userId','Teilnehmer-ID','']].forEach(([name,label,hint])=>{
  const wrapper=node('label',label),input=document.createElement('input');input.name=name;input.required=true;input.maxLength=name==='url'?500:35;input.placeholder=hint;
  if(name!=='url')input.pattern='[A-Za-z0-9]{1,35}';wrapper.append(input);form.append(wrapper);
 });
 const save=node('button',tx('eb.2','Verbindung speichern'));save.type='submit';form.append(save);
 const actions=document.createElement('div');actions.className='actions';
 const keys=node('button',tx('eb.3','Schlüssel erzeugen und senden (INI/HIA)')),bank=node('button',tx('eb.4','Bankschlüssel abholen (HPB)')),reset=node('button',tx('eb.5','Zurücksetzen'));
 [keys,bank,reset].forEach(b=>{b.type='button';b.className='secondary';actions.append(b);});
 const prints=document.createElement('pre');prints.setAttribute('aria-label',tx('eb.6','Fingerabdrücke'));prints.hidden=true;
 const messagesBox=document.createElement('div');messagesBox.hidden=true;
 const fetchButton=node('button',tx('eb.7','Nachrichten abholen'));fetchButton.type='button';
 const list=document.createElement('div');list.className='ebics-messages';
 const autoBox=document.createElement('div');autoBox.className='ebics-auto-fetch';
 const autoLabel=node('label',tx('eb.8','Automatisch abrufen')),autoToggle=document.createElement('input');autoToggle.type='checkbox';autoToggle.name='autoFetch';autoLabel.prepend(autoToggle);
 const intervalLabel=node('label',tx('eb.9','alle ')),interval=document.createElement('select');interval.name='interval';[[5,tx('eb.10','5 Minuten')],[15,tx('eb.11','15 Minuten')],[30,tx('eb.12','30 Minuten')],[60,tx('eb.13','1 Stunde')],[240,tx('eb.14','4 Stunden')],[1440,tx('eb.15','24 Stunden')]].forEach(([v,l])=>{const o=node('option',l);o.value=v;interval.append(o);});intervalLabel.append(interval);
 const autoSave=node('button',tx('eb.16','Übernehmen'));autoSave.type='button';autoSave.className='secondary';const autoInfo=node('small','');
 autoBox.append(autoLabel,intervalLabel,autoSave,autoInfo);
 messagesBox.append(node('h3',tx('eb.17','Abgeholte Nachrichten')),autoBox,node('p',tx('eb.18','Nichts wird automatisch angelegt: Nachrichten erst ansehen, dann importieren oder verwerfen.')),fetchButton,list);
 section.append(node('h2',tx('eb.0','EBICS-Bankanbindung')),node('p',tx('eb.19','Eigener Teilnehmer für den Abruf von Akkreditivnachrichten (MT700/707/710/760). Ablauf: Verbindung speichern, Schlüssel senden, Teilnehmer bankseitig freigeben lassen (INI-Brief mit den Fingerabdrücken), danach Bankschlüssel abholen.')),message,info,form,actions,prints,messagesBox);
 document.querySelector('main').append(section);
 const labels={NEW:tx('eb.20','Neu – Schlüssel fehlen'),KEYS_SENT:tx('eb.21','Schlüssel gesendet – wartet auf Freigabe durch die Bank'),ACTIVE:tx('eb.22','Aktiv'),ERROR:tx('eb.23','Fehler')};
 let busy=false;
 function badge(c){nav.textContent=tx('eb.0','EBICS-Bankanbindung')+(c&&c.newMessages>0?' ('+c.newMessages+')':'');}
 async function refreshBadge(){try{badge(await json('/api/ebics/connection'));}catch(ignored){}}
 function showPrints(p){prints.hidden=false;prints.textContent=tx('eb.24','Fingerabdrücke (SHA-256)\nA006 Signatur:      ')+p.a005+tx('eb.25','\nE002 Verschlüsselung: ')+p.e002+tx('eb.26','\nX002 Authentifizierung: ')+p.x002;}
 async function refresh(){
  message.textContent='';
  try{
   const c=await json('/api/ebics/connection');
   info.replaceChildren(node('p',tx('eb.27','Status: ')+(c.configured?(labels[c.status]||c.status):tx('eb.28','nicht eingerichtet'))+(c.lastError?' · '+c.lastError:'')));
   if(!c.encryptionConfigured)info.append(node('p',tx('eb.29','Hinweis: EBICS_ENCRYPTION_KEY ist auf dem Server nicht gesetzt – Schlüssel können noch nicht erzeugt werden.')));
   ['url','hostId','partnerId','userId'].forEach(n=>{form.elements[n].value=c[n]||'';form.elements[n].disabled=c.configured&&c.status!=='NEW'&&c.status!=='ERROR';});
   save.disabled=c.configured&&c.status!=='NEW'&&c.status!=='ERROR';
   keys.disabled=!c.configured||!c.encryptionConfigured||c.status==='KEYS_SENT'||c.status==='ACTIVE';
   bank.disabled=c.status!=='KEYS_SENT';reset.disabled=!c.configured||c.status==='NEW';
   badge(c);autoToggle.checked=Boolean(c.autoFetch);interval.value=String(c.fetchIntervalMinutes||15);
   autoInfo.textContent=c.lastFetchAt?tx('eb.30','Zuletzt abgerufen: ')+String(c.lastFetchAt).replace('T',' ').slice(0,16)+(c.lastFetchResult?' · '+c.lastFetchResult:''):tx('eb.31','Noch kein Abruf.');
   messagesBox.hidden=!(c.status==='ACTIVE'&&typeof can==='function'&&can('SWIFT_IMPORT'));
   if(!messagesBox.hidden)await loadMessages();
   if(c.configured&&c.status!=='NEW'){try{showPrints(await json('/api/ebics/connection/fingerprints'));}catch(e){prints.hidden=true;}}else prints.hidden=true;
  }catch(error){message.textContent=error.message;}
 }
 const statusLabel={NEW:tx('eb.32','Neu'),IMPORTED:tx('eb.33','Importiert'),DISCARDED:tx('eb.34','Verworfen')};
 async function loadMessages(){
  try{
   const rows=await json('/api/ebics/messages');list.replaceChildren();
   if(!rows.length){list.append(node('p',tx('eb.35','Noch keine Nachrichten abgeholt.')));return;}
   rows.forEach(m=>{
    const row=document.createElement('article');row.className='membership-row';
    row.append(node('b',m.messageType+' · '+(m.reference||'ohne Referenz')),node('span',' '+(statusLabel[m.status]||m.status)+' · '+String(m.receivedAt||'').replace('T',' ').slice(0,16)));
    if(m.importable){
     const view=node('button',tx('eb.36','Ansehen')),imp=node('button',tx('eb.37','Importieren')),drop=node('button',tx('eb.38','Verwerfen'));
     [view,imp,drop].forEach(b=>{b.type='button';b.className='secondary';row.append(b);});
     view.onclick=async()=>{try{const p=await json('/api/ebics/messages/'+m.id+'/preview');
       message.textContent=[(p.valid?tx('eb.39','Import möglich'):tx('eb.40','Import nicht möglich'))+tx('eb.41',' · Referenz ')+(p.reference||'-')+(p.currency?' · '+p.currency+' '+p.amount:''),...(p.errors||[]),...(p.warnings||[])].join(' | ');}
      catch(error){message.textContent=error.message;}};
     imp.onclick=async()=>{if(await confirmAction(tx('eb.42','Nachricht ')+m.messageType+' '+(m.reference||'')+tx('eb.43',' jetzt importieren?')))run(()=>json('/api/ebics/messages/'+m.id+'/import',{method:'POST'}),tx('eb.44','Importiert.'));};
     drop.onclick=async()=>{if(await confirmAction(tx('eb.45','Nachricht verwerfen? Sie wird nicht importiert.')))run(()=>json('/api/ebics/messages/'+m.id+'/discard',{method:'POST'}),tx('eb.46','Verworfen.'));};
    }
    list.append(row);
   });
  }catch(error){list.replaceChildren(node('p',error.message));}
 }
 fetchButton.onclick=()=>run(async()=>{const r=await json('/api/ebics/messages/fetch',{method:'POST'});message.textContent='';
   return {done:r};},'');
 async function run(task,done){
  if(busy)return;busy=true;message.textContent='';
  try{const result=await task();if(result&&result.a005)showPrints(result);if(result&&result.done){const r=result.done;message.textContent=r.fetched+tx('eb.47',' neue Nachricht(en), ')+r.alreadyKnown+tx('eb.48',' bereits bekannt')+(r.errors&&r.errors.length?tx('eb.49',' · Fehler: ')+r.errors.join('; '):'')+'.';}else message.textContent=done;}
  catch(error){message.textContent=error.message;}
  finally{busy=false;await refreshKeepMessage();}
 }
 async function refreshKeepMessage(){const text=message.textContent;await refresh();if(text)message.textContent=text;}
 const post=(path)=>json('/api/ebics/connection'+path,{method:'POST'});
 form.onsubmit=event=>{event.preventDefault();return run(()=>json('/api/ebics/connection',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(Object.fromEntries(['url','hostId','partnerId','userId'].map(n=>[n,form.elements[n].value.trim()])))}),tx('eb.50','Verbindung gespeichert.'));};
 keys.onclick=()=>run(()=>post('/keys'),tx('eb.51','Schlüssel erzeugt und an die Bank gesendet. Bitte den Teilnehmer bankseitig freigeben lassen.'));
 bank.onclick=()=>run(()=>post('/bank-keys'),tx('eb.52','Bankschlüssel abgeholt – die Verbindung ist aktiv.'));
 reset.onclick=async()=>{if(await confirmAction(tx('eb.53','Schlüssel verwerfen und die Einrichtung neu beginnen? Der Teilnehmer muss bankseitig ggf. zurückgesetzt werden.')))run(()=>post('/reset'),tx('eb.54','Zurückgesetzt.'));};
 autoSave.onclick=()=>run(()=>json('/api/ebics/connection/auto-fetch',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({enabled:autoToggle.checked,intervalMinutes:Number(interval.value)})}),autoToggle.checked?tx('eb.55','Automatischer Abruf eingeschaltet.'):tx('eb.56','Automatischer Abruf ausgeschaltet.'));
 nav.onclick=()=>{appNavigate('ebics',nav);return refresh();};
 refreshBadge();
}
