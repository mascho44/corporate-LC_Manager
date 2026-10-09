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
 section.append(node('h2','EBICS-Bankanbindung'),node('p','Eigener Teilnehmer für den Abruf von Akkreditivnachrichten (MT700/707/710/760). Ablauf: Verbindung speichern, Schlüssel senden, Teilnehmer bankseitig freigeben lassen (INI-Brief mit den Fingerabdrücken), danach Bankschlüssel abholen.'),message,info,form,actions,prints);
 document.querySelector('main').append(section);
 const labels={NEW:'Neu – Schlüssel fehlen',KEYS_SENT:'Schlüssel gesendet – wartet auf Freigabe durch die Bank',ACTIVE:'Aktiv',ERROR:'Fehler'};
 let busy=false;
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
   if(c.configured&&c.status!=='NEW'){try{showPrints(await json('/api/ebics/connection/fingerprints'));}catch(e){prints.hidden=true;}}else prints.hidden=true;
  }catch(error){message.textContent=error.message;}
 }
 async function run(task,done){
  if(busy)return;busy=true;message.textContent='';
  try{const result=await task();if(result&&result.a005)showPrints(result);message.textContent=done;}
  catch(error){message.textContent=error.message;}
  finally{busy=false;await refreshKeepMessage();}
 }
 async function refreshKeepMessage(){const text=message.textContent;await refresh();if(text)message.textContent=text;}
 const post=(path)=>json('/api/ebics/connection'+path,{method:'POST'});
 form.onsubmit=event=>{event.preventDefault();return run(()=>json('/api/ebics/connection',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(Object.fromEntries(['url','hostId','partnerId','userId'].map(n=>[n,form.elements[n].value.trim()])))}),'Verbindung gespeichert.');};
 keys.onclick=()=>run(()=>post('/keys'),'Schlüssel erzeugt und an die Bank gesendet. Bitte den Teilnehmer bankseitig freigeben lassen.');
 bank.onclick=()=>run(()=>post('/bank-keys'),'Bankschlüssel abgeholt – die Verbindung ist aktiv.');
 reset.onclick=async()=>{if(await confirmAction('Schlüssel verwerfen und die Einrichtung neu beginnen? Der Teilnehmer muss bankseitig ggf. zurückgesetzt werden.'))run(()=>post('/reset'),'Zurückgesetzt.');};
 nav.onclick=()=>{appNavigate('ebics',nav);return refresh();};
}
