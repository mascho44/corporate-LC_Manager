// Fills a demo workspace (corporate profile) with realistic, fictitious cases. Dates are relative to today, so every run is fresh.
// Usage: LCM_URL=https://lc.ostms.de LCM_USER=... LCM_PASSWORD=... LCM_TOTP=123456 node scripts/seed-demo.cjs
// Needs a platform administrator. Idempotent: existing demo files (by reference) are skipped; the workspace code is "demo".
// All companies, banks and people are invented. Nothing here is a real transaction.
const base=(process.env.LCM_URL||'').replace(/\/$/,''),cookies=new Map();let csrf='';
if(!base||!process.env.LCM_USER||!process.env.LCM_PASSWORD){console.error('Set LCM_URL, LCM_USER, LCM_PASSWORD (and LCM_TOTP if two-factor is on).');process.exit(1);}
async function call(path,method='GET',body,type='application/json',okStatus=[200,201,204]){
 const headers={Cookie:[...cookies].map(([k,v])=>`${k}=${v}`).join(';')};if(csrf)headers['X-CSRF-TOKEN']=csrf;if(body!==undefined)headers['Content-Type']=type;
 const response=await fetch(base+path,{method,headers,body:body===undefined?undefined:type==='application/json'?JSON.stringify(body):body});
 for(const cookie of response.headers.getSetCookie()){const pair=cookie.split(';')[0],at=pair.indexOf('=');cookies.set(pair.slice(0,at),pair.slice(at+1));}
 const text=await response.text();if(!okStatus.includes(response.status))throw Error(`${method} ${path}: HTTP ${response.status} ${text.slice(0,200)}`);
 return text?JSON.parse(text):null;
}
const day=n=>{const d=new Date();d.setUTCDate(d.getUTCDate()+n);return d;};
const iso=n=>day(n).toISOString().slice(0,10),yymmdd=n=>iso(n).slice(2).replaceAll('-','');
const money=v=>String(v).replace('.',',');
/** Minimal but realistic MT700: issued [issueDay] days from today, expires [expiryDay], latest shipment [shipDay]. */
function mt700({ref,issue,expiry,ship,place,currency,amount,applicant,beneficiary,goods,docs,bank}){
 return [`:27:1/1`,`:40A:IRREVOCABLE`,`:20:${ref}`,`:31C:${yymmdd(issue)}`,`:40E:UCP LATEST VERSION`,`:31D:${yymmdd(expiry)}${place}`,`:50:${applicant}`,`:59:${beneficiary}`,
  `:32B:${currency}${money(amount)}`,`:41D:${bank}\nBY NEGOTIATION`,`:43P:ALLOWED`,`:43T:ALLOWED`,`:44C:${yymmdd(ship)}`,`:45A:${goods}`,
  `:46A:${docs}`,`:47A:ALL DOCUMENTS MUST BE ISSUED IN ENGLISH\nINSURANCE COVERED BY APPLICANT`,`:71D:ALL BANKING CHARGES OUTSIDE ISSUING BANK FOR ACCOUNT OF BENEFICIARY`,
  `:48:21 DAYS`,`:49:WITHOUT`].join('\n');
}
const DOCS='+SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS\n+PACKING LIST IN 2 COPIES\n+FULL SET OF CLEAN ON BOARD BILLS OF LADING\n+CERTIFICATE OF ORIGIN';
// role: BENEFICIARY = we are exporter (documents to deliver); APPLICANT = we are importer (we opened it)
const LCS=[
 {key:'EX-1',role:'BENEFICIARY',issue:-40,expiry:62,ship:41,place:'HAMBURG',currency:'EUR',amount:480000,applicant:'ALMARINE TRADING LLC\nJEBEL ALI FREE ZONE\nDUBAI UAE',beneficiary:'NORDMETALL MASCHINENBAU GMBH\nINDUSTRIESTR 12\n28199 BREMEN GERMANY',goods:'1 UNIT CNC MILLING MACHINE TYPE NM-5X\nINCOTERMS 2020 CIF JEBEL ALI',bank:'EMIRATES COMMERCIAL BANK PJSC',payment:{days:-3,part:0}},
 {key:'EX-2',role:'BENEFICIARY',issue:-75,expiry:9,ship:-8,place:'GERMANY',currency:'USD',amount:215000,applicant:'KARACHI TEXTILE IMPORTS LTD\nI I CHUNDRIGAR ROAD\nKARACHI PAKISTAN',beneficiary:'NORDMETALL MASCHINENBAU GMBH\nINDUSTRIESTR 12\n28199 BREMEN GERMANY',goods:'SPARE PARTS FOR WEAVING MACHINES\nAS PER PROFORMA INVOICE 2026/0412',bank:'INDUS TRADE BANK LTD',payment:{days:-1,part:0.4}},
 {key:'EX-3',role:'BENEFICIARY',issue:-20,expiry:130,ship:95,place:'GERMANY',currency:'EUR',amount:1250000,applicant:'IRBID INDUSTRIAL SUPPLY CO\nAMMAN JORDAN',beneficiary:'NORDMETALL MASCHINENBAU GMBH\nINDUSTRIESTR 12\n28199 BREMEN GERMANY',goods:'2 UNITS HYDRAULIC PRESS TYPE HP-400\nINCOTERMS 2020 CFR AQABA',bank:'AMMAN MERCHANT BANK'},
 {key:'IM-1',role:'APPLICANT',issue:-30,expiry:45,ship:22,place:'SHANGHAI',currency:'USD',amount:360000,applicant:'NORDMETALL MASCHINENBAU GMBH\nINDUSTRIESTR 12\n28199 BREMEN GERMANY',beneficiary:'SHANGHAI PRECISION CASTINGS CO LTD\nPUDONG NEW AREA\nSHANGHAI CHINA',goods:'STEEL CASTINGS FOR MACHINE FRAMES\nAS PER CONTRACT NM-2026-77\nFOB SHANGHAI',bank:'NORDKUESTE BANK AG'},
 {key:'IM-2',role:'APPLICANT',issue:-12,expiry:80,ship:60,place:'MUMBAI',currency:'EUR',amount:94500,applicant:'NORDMETALL MASCHINENBAU GMBH\nINDUSTRIESTR 12\n28199 BREMEN GERMANY',beneficiary:'BHARAT FORGINGS PVT LTD\nNHAVA SHEVA\nMUMBAI INDIA',goods:'FORGED SHAFTS, 600 PIECES\nFCA MUMBAI',bank:'NORDKUESTE BANK AG'},
 {key:'EX-4',role:'BENEFICIARY',issue:-120,expiry:-14,ship:-40,place:'GERMANY',currency:'EUR',amount:76000,applicant:'LAGOS ENGINEERING SERVICES LTD\nAPAPA LAGOS NIGERIA',beneficiary:'NORDMETALL MASCHINENBAU GMBH\nINDUSTRIESTR 12\n28199 BREMEN GERMANY',goods:'CONTROL CABINETS FOR PUMP STATION',bank:'WEST AFRICAN COMMERCIAL BANK',payment:{days:-30,part:1}}
];
const GUARANTEES=[
 {reference:'NM-AVAL-2601',guaranteeType:'ADVANCE_PAYMENT',bank:'Nordkueste Bank AG',beneficiary:'Almarine Trading LLC, Dubai',currency:'EUR',amount:96000,issue:-38,from:-38,expiry:75,claim:105,auto:false,underlying:'Anzahlung 20 % zu Liefervertrag AL-2026-14',ref:'ALM-AG-0098',status:'ACTIVE'},
 {reference:'NM-AVAL-2602',guaranteeType:'PERFORMANCE',bank:'Nordkueste Bank AG',beneficiary:'Stadtwerke Musterhafen AG',currency:'EUR',amount:210000,issue:-300,from:-300,expiry:36,claim:66,auto:true,notice:30,underlying:'Vertragserfuellung Pumpwerk Musterhafen Los 3',ref:'SWM-VE-4471',status:'ACTIVE',request:{status:'ISSUED'}},
 {reference:'NM-AVAL-2603',guaranteeType:'WARRANTY',bank:'Hanseatische Handelsbank',beneficiary:'Baltic Energy Systems OY, Helsinki',currency:'EUR',amount:58500,issue:-620,from:-620,expiry:12,claim:42,auto:false,underlying:'Gewaehrleistung 24 Monate Steuerungsanlage Projekt BES-17',ref:'BES-W-0211',status:'ACTIVE'},
 {reference:'NM-AVAL-2604',guaranteeType:'BID',bank:'Hanseatische Handelsbank',beneficiary:'Ministry of Water Resources, Amman',currency:'USD',amount:120000,issue:-10,from:-10,expiry:80,claim:80,auto:false,underlying:'Bietungsgarantie Ausschreibung WR-2026/31',ref:'MWR-BB-31',status:'ACTIVE'},
 {reference:'NM-AVAL-2605',guaranteeType:'PERFORMANCE',bank:'Nordkueste Bank AG',beneficiary:'Kraftwerke Nordsee GmbH',currency:'EUR',amount:340000,issue:5,from:5,expiry:400,claim:430,auto:true,notice:60,underlying:'Vertragserfuellung Lieferung Hydraulikpressen',ref:'KNS-VE-0902',status:'REQUESTED',request:{status:'SENT',due:6,wording:'Die Bank verpflichtet sich unwiderruflich, auf erste schriftliche Anforderung des Beguenstigten bis zu EUR 340.000 zu zahlen, sofern der Auftraggeber seinen Verpflichtungen aus dem Liefervertrag nicht nachgekommen ist.'}},
 {reference:'NM-AVAL-2510',guaranteeType:'ADVANCE_PAYMENT',bank:'Nordkueste Bank AG',beneficiary:'Gulf Steel Works, Doha',currency:'USD',amount:45000,issue:-400,from:-400,expiry:-30,claim:-10,auto:false,underlying:'Anzahlung Ersatzteillieferung 2025',ref:'GSW-AG-77',status:'RETURNED'}
];
const LINES=[
 {name:'Avallinie Nordkueste Bank',bank:'Nordkueste Bank AG',bic:'NORDDEHHXXX',currency:'EUR',amount:900000,validUntil:iso(300),warnPercent:80,note:'Rahmen fuer Garantien; Jahrespruefung im Herbst'},
 {name:'Linie Gewaehrleistung Hanseatische',bank:'Hanseatische Handelsbank',currency:'EUR',amount:150000,guaranteeType:'WARRANTY',validUntil:iso(200),warnPercent:75,active:true},
 {name:'Avallinie Hanseatische (USD)',bank:'Hanseatische Handelsbank',currency:'USD',amount:200000,validUntil:iso(200),warnPercent:80}
];
(async()=>{
 let me=await call('/api/auth/login','POST',{username:process.env.LCM_USER,password:process.env.LCM_PASSWORD});
 if(me.requiresTotp||me.totpRequired||me.requiresTotpSetup){if(!process.env.LCM_TOTP)throw Error('Two-factor is on: set LCM_TOTP to the current code.');await call('/api/auth/login/totp','POST',{code:process.env.LCM_TOTP});}
 csrf=(await call('/api/auth/me')).csrfToken;
 const username=process.env.LCM_USER;
 let tenant=(await call('/api/platform/tenants')).find(t=>t.code==='demo');
 if(!tenant){tenant=await call('/api/platform/tenants','POST',{code:'demo',name:'Demo Nordmetall Maschinenbau',defaultLanguage:'de',bankEnabled:false,corporateEnabled:true,administrator:{username}});console.log('Demo-Mandant angelegt');}
 await call('/api/tenants/'+tenant.id+'/select','POST');csrf=(await call('/api/auth/me')).csrfToken;
 const existing=new Map((await call('/api/lcs')).map(l=>[l.reference,l]));
 const have=await call('/api/customers');
 for(const c of [{name:'Almarine Trading LLC',customerNumber:'K-1001',bic:'ECBDAEAAXXX'},{name:'Karachi Textile Imports Ltd',customerNumber:'K-1002'},{name:'Stadtwerke Musterhafen AG',customerNumber:'K-2001'}])
  if(!have.some(h=>h.name===c.name))await call('/api/customers','POST',c);
 let n=0;
 for(const spec of LCS){
  const ref='DEMO-'+spec.key+'-'+(2600+n++);
  if(existing.has(ref))continue;
  const lc=await call('/api/lcs/import/mt700','POST',mt700({...spec,ref}),'text/plain');
  if(spec.role==='APPLICANT')await call(`/api/lcs/${lc.id}/party-role`,'PUT',{role:'APPLICANT'});
  if(spec.payment&&spec.role==='BENEFICIARY'){
   const part=spec.payment.part>0&&spec.payment.part<1?spec.amount*spec.payment.part:spec.amount;
   await call(`/api/lcs/${lc.id}/payments`,'POST',{receivedOn:iso(spec.payment.days),amount:part,currency:spec.currency,reference:'Zahlung '+ref,note:'Demo'});
  }
  console.log('Akkreditiv',ref);
 }
 const known=new Set((await call('/api/guarantees')).map(g=>g.reference));
 for(const g of GUARANTEES){
  if(known.has(g.reference))continue;
  const created=await call('/api/guarantees','POST',{reference:g.reference,guaranteeType:g.guaranteeType,bank:g.bank,beneficiary:g.beneficiary,currency:g.currency,amount:g.amount,issueDate:iso(g.issue),validFrom:iso(g.from),expiryDate:iso(g.expiry),claimUntil:iso(g.claim),autoExtension:!!g.auto,extensionNoticeDays:g.notice||null,underlying:g.underlying,beneficiaryReference:g.ref,status:g.status==='RETURNED'?'ACTIVE':g.status});
  if(g.status==='RETURNED')await call(`/api/guarantees/${created.id}/status`,'POST',{status:'RETURNED',date:iso(-12),note:'Original vom Beguenstigten zurueckgesandt'});
  if(g.request){
   await call(`/api/guarantees/${created.id}/request`,'PUT',{recipient:g.bank,ourReference:g.reference,wording:g.request.wording||'Wortlaut gemaess Vorlage des Beguenstigten.',remarks:'',responseDue:g.request.due!=null?iso(g.request.due):null});
   if(g.request.status!=='DRAFT')await call(`/api/guarantees/${created.id}/request/status`,'POST',{status:g.request.status,date:iso(-1),note:'Demo',bankReference:null});
  }
  console.log('Garantie',g.reference);
 }
 const lines=(await call('/api/credit-lines')).lines||[];
 for(const l of LINES)if(!lines.some(x=>x.name===l.name))await call('/api/credit-lines','POST',{active:true,...l});
 console.log('Fertig. Mandant "demo" ist befuellt.');
})().catch(e=>{console.error(e.message);process.exit(1);});
