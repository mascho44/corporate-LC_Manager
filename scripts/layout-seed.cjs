// Sample data for the disposable layout installation ONLY (localhost:18086, synthetic credentials from docker-compose.tenant-test.yml).
const fs=require('node:fs'),crypto=require('node:crypto');
const base='http://127.0.0.1:18086',cookies=new Map(),stateFile=process.env.LAYOUT_STATE||'layout-stack.json';let csrf='';
const password='SyntheticOnly123!';
function totp(secret){
 let buffer=0,bits=0;const bytes=[];for(const c of secret){buffer=(buffer<<5)|'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(c);bits+=5;if(bits>=8){bytes.push((buffer>>>(bits-8))&255);bits-=8;}}
 const counter=Buffer.alloc(8);counter.writeBigUInt64BE(BigInt(Math.floor(Date.now()/30000)));const h=crypto.createHmac('sha1',Buffer.from(bytes)).update(counter).digest(),offset=h[19]&15;
 return String(((h[offset]&127)<<24|h[offset+1]<<16|h[offset+2]<<8|h[offset+3])%1000000).padStart(6,'0');
}
async function call(path,method='GET',body,type='application/json'){
 const headers={Cookie:[...cookies].map(([k,v])=>`${k}=${v}`).join(';')};if(csrf)headers['X-CSRF-TOKEN']=csrf;
 if(body!==undefined&&type!=='multipart')headers['Content-Type']=type;
 const response=await fetch(base+path,{method,headers,body:body===undefined?undefined:type==='application/json'?JSON.stringify(body):body});
 for(const cookie of response.headers.getSetCookie()){const pair=cookie.split(';')[0],at=pair.indexOf('=');cookies.set(pair.slice(0,at),pair.slice(at+1));}
 const content=await response.text();if(response.status>=400)throw new Error(`${method} ${path}: HTTP ${response.status} ${content.slice(0,160)}`);
 return content?JSON.parse(content):null;
}
function pdf(lines){
 const esc=t=>t.replace(/[\\()]/g,'\\$&'),stream='BT /F1 11 Tf 40 800 Td 15 TL '+lines.map((l,i)=>(i?'T* ':'')+'('+esc(l)+') Tj').join(' ')+' ET';
 const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Count 1 /Kids [4 0 R] >>','<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents 5 0 R >>','<< /Length '+Buffer.byteLength(stream)+' >>\nstream\n'+stream+'\nendstream'];
 let out='%PDF-1.4\n';const offsets=[];objects.forEach((o,i)=>{offsets.push(Buffer.byteLength(out));out+=(i+1)+' 0 obj\n'+o+'\nendobj\n';});
 const xref=Buffer.byteLength(out);out+='xref\n0 '+(objects.length+1)+'\n0000000000 65535 f \n'+offsets.map(at=>String(at).padStart(10,'0')+' 00000 n \n').join('')+'trailer\n<< /Size '+(objects.length+1)+' /Root 1 0 R >>\nstartxref\n'+xref+'\n%%EOF\n';
 return Buffer.from(out);
}
const upload=(bytes,name,type='application/pdf')=>{const f=new FormData();f.append('file',new Blob([bytes],{type}),name);return f;};
const yymmdd=days=>{const d=new Date(Date.now()+days*864e5);return String(d.getFullYear()%100).padStart(2,'0')+String(d.getMonth()+1).padStart(2,'0')+String(d.getDate()).padStart(2,'0');};
function mt700(reference,shipmentDays){
 return `:20:${reference}\n:27:1/1\n:40A:IRREVOCABLE\n:31C:260901\n:31D:${yymmdd(60)}HAMBURG\n:50:EXAMPLE VISION LTD, SHANGHAI\n:59:LAYOUT EXPORT GMBH, STUTTGART\n:32B:EUR131100,00\n:44A:SHANGHAI\n:44B:HAMBURG\n:44C:${yymmdd(shipmentDays)}\n`+
  `:45A:MACHINE PARTS, AS PER PROFORMA INVOICE\n:46A:+SIGNED COMMERCIAL INVOICE IN 3 COPIES\n+PACKING LIST IN 2 COPIES\n+FULL SET OF CLEAN ON BOARD BILLS OF LADING\n+CERTIFICATE OF ORIGIN\n+INSURANCE CERTIFICATE COVERING 110 PCT\n:71D:ALL CHARGES OUTSIDE ISSUING BANK FOR BENEFICIARY\n`;
}
async function seedTenant(code,name,bank,corporate,tag){
 const created=await call('/api/platform/tenants','POST',{code,name,defaultLanguage:'de',bankEnabled:bank,corporateEnabled:corporate});
 await call('/api/tenants/'+created.id+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 const step=async(label,work)=>{try{return await work();}catch(error){console.log('  skipped '+label+': '+error.message);return null;}};
 await step('company',()=>call('/api/companies','POST',{legalName:'LAYOUT EXPORT GMBH'}));
 const lc=await call('/api/lcs/import/mt700','POST',mt700('LCB'+tag+'0001',9),'text/plain');
 const importer=await step('second LC',()=>call('/api/lcs/import/mt700','POST',mt700('LCB'+tag+'0002',40),'text/plain'));
 if(corporate&&importer)await step('importer role',()=>call('/api/lcs/'+importer.id+'/party-role','PUT',{role:'APPLICANT'}));
 await step('packing list',()=>call('/api/lcs/'+lc.id+'/documents?type=PACKING_LIST','POST',upload(pdf(['PACKING LIST','Packing list no. PL-1','LC '+lc.reference,'10 cartons, net 120 kg, gross 140 kg']),'packing-list.pdf'),'multipart'));
 await step('invoice',()=>call('/api/lcs/'+lc.id+'/documents?type=COMMERCIAL_INVOICE','POST',upload(pdf(['COMMERCIAL INVOICE','Invoice no. INV-1','LC '+lc.reference,'Total EUR 60229,56']),'rechnung-sehr-langer-dateiname-fuer-den-umbruch-test-seiten-11-14.pdf'),'multipart'));
 await step('customer',()=>call('/api/customers','POST',{name:'Layout Export GmbH',customerNumber:'4711',accountNumber:'0012345678',iban:'DE89 3704 0044 0532 0130 00',bic:'COBADEFFXXX'}));
 await step('capture token',()=>call('/api/capture-tokens','POST',{name:'Poststelle Scanner 2'}));
 await step('template',async()=>{
  const sample=Buffer.from(await (await fetch(base+'/api/document-templates/sample?type=PACKING_LIST',{headers:{Cookie:[...cookies].map(([k,v])=>`${k}=${v}`).join(';')}})).arrayBuffer());
  const f=new FormData();f.append('type','PACKING_LIST');f.append('file',new Blob([sample],{type:'application/vnd.openxmlformats-officedocument.wordprocessingml.document'}),'Packliste-Vorlage.docx');
  return call('/api/document-templates','POST',f,'multipart');
 });
 await step('submission letter',()=>call('/api/inbox','POST',upload(pdf(['Dokumenteneinreichung','','Akkreditivnummer            '+lc.reference,'Ihre Referenz               BW000AE0000001','Akkreditiv ueber             EUR 131.100,00 +05%/-05%',
  'Eroeffnende Bank             EXAMPLE BANK OF TRADE CO. LTD.','Auftraggeber                EXAMPLE VISION LTD','','Sehr geehrte Damen und Herren,','anbei erhalten Sie folgende Dokumente zur Ausnutzung des Akkreditivs.',
  'Dokumentengegenwert / Waehrung:   EUR 60.229,56','','Wir bitten um Gutschrift des Dokumentengegenwertes zu Gunsten:','[X] EUR-Konto Nummer: DE89 3704 0044 0532 0130 00','[ ] FW-Konto Nummer:','Mit freundlichen Gruessen']),'einreichung-scan-0815.pdf'),'multipart'));
 await step('inbox invoice',()=>call('/api/inbox','POST',upload(pdf(['COMMERCIAL INVOICE','Invoice no. INV-2','LC '+lc.reference]),'ORIGINALE - 20260805 - Dokumente blanko-Seiten-11-14.pdf'),'multipart'));
 return created;
}
(async()=>{
 if(process.argv[2]==='code'){const s=JSON.parse(fs.readFileSync(stateFile));console.log(totp(s.totpSecret));return;}
 const setup=await call('/api/auth/login','POST',{username:'admin',password});
 if(!setup.requiresTotpSetup)throw new Error('Expected a fresh installation (TOTP enrollment).');
 await call('/api/auth/login/totp','POST',{code:totp(setup.secret)});csrf=(await call('/api/auth/me')).csrfToken;
 const corp=await seedTenant('layout-corp','Layout Corporate (nur Corporate)',false,true,'1');
 const both=await seedTenant('layout-both','Layout Bank und Corporate',true,true,'2');
 fs.writeFileSync(stateFile,JSON.stringify({totpSecret:setup.secret,tenants:{corporate:corp.id,both:both.id}},null,1));
 console.log('\nLayout installation ready: http://127.0.0.1:18086/  (user admin, password '+password+', code: scripts/layout-stack.sh code)');
 console.log('Tenant codes: layout-corp (corporate only), layout-both (bank and corporate).  Stop with: scripts/layout-stack.sh down');
})().catch(error=>{console.error(error);process.exit(1);});
