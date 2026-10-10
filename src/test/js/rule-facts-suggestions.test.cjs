const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/rule-facts.js'),'utf8');
const settle=()=>new Promise(r=>setImmediate(r));
function setup({overview,confirm=true}){
 const calls=[],alerts=[],confirmations=[];let click;
 const globals={document:{getElementById:()=>null,createElement:()=>({}),body:{append(){}},addEventListener:(t,h)=>{click=h;}},activeLc:{id:'lc-1'},can:()=>true,esc:v=>String(v),
  alert:m=>alerts.push(m),confirmAction:async m=>{confirmations.push(m);return confirm;},show:async id=>calls.push({refresh:id}),activateDossierSection:s=>calls.push({section:s}),
  json:async(url,options)=>{calls.push({url,method:options?.method});
   if(url.endsWith('/definitions'))return {document:[{field:'DOCUMENT_PACKAGE_COUNT',label:'Packstückzahl'}],lc:[{field:'LC_BENEFICIARY_ADDRESS_COUNTRY',label:'LC: Land der Begünstigtenadresse'}]};
   if(url.endsWith('/suggestions/apply'))return {lcFields:1,documentFields:2,documents:1};
   if(url.endsWith('/suggestions'))return overview;return {};}};
 vm.runInNewContext(source,globals);
 return {calls,alerts,confirmations,press:async()=>{click({target:{closest:()=>({hasAttribute:()=>true})}});await settle();await settle();await settle();}};
}
const overview={lc:[{field:'LC_BENEFICIARY_ADDRESS_COUNTRY',value:'China',current:null},{field:'LC_APPLICANT_ADDRESS_COUNTRY',value:'Switzerland',current:'Hong Kong'}],
 documents:[{documentId:'d1',filename:'packing.pdf',suggestions:[{field:'DOCUMENT_PACKAGE_COUNT',value:'48',current:null}]}],total:3};
test('the preview lists only proposals for empty fields and the apply call follows the confirmation',async()=>{
 const s=setup({overview});await s.press();
 assert.equal(s.confirmations.length,1);const text=s.confirmations[0];
 assert.match(text,/2 erkannte Angaben übernehmen/);assert.match(text,/LC: Land der Begünstigtenadresse = China/);assert.match(text,/packing\.pdf: Packstückzahl = 48/);
 assert.doesNotMatch(text,/Switzerland/,'a field that already holds a value is not part of the preview');assert.match(text,/zurückgesetzt/);
 assert.ok(s.calls.some(c=>c.method==='POST'&&c.url==='/api/lcs/lc-1/rule-facts/suggestions/apply'));
 assert.match(s.alerts.at(-1),/1 LC-Angaben und 2 Dokumentangaben in 1 Dokumenten übernommen/);
 assert.ok(s.calls.some(c=>c.refresh==='lc-1'));assert.ok(s.calls.some(c=>c.section==='checks'));
});
test('cancelling the confirmation applies nothing',async()=>{
 const s=setup({overview,confirm:false});await s.press();
 assert.equal(s.calls.some(c=>c.method==='POST'),false);assert.equal(s.alerts.length,0);
});
test('without new proposals the user is told so and nothing is posted',async()=>{
 const s=setup({overview:{lc:[{field:'LC_APPLICANT_ADDRESS_COUNTRY',value:'Switzerland',current:'Hong Kong'}],documents:[],total:1}});await s.press();
 assert.equal(s.confirmations.length,0);assert.match(s.alerts[0],/keine neuen Vorschläge/);assert.equal(s.calls.some(c=>c.method==='POST'),false);
});
test('the dossier review offers the button only to users who may edit documents',()=>{
 const app=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
 assert.match(app,/can\('DOCUMENT_UPLOAD'\)\?' <button type="button" class="secondary" data-apply-suggestions>Erkannte Prüfdaten übernehmen<\/button>':''/);
});
