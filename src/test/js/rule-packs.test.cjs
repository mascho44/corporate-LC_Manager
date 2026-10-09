const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/rule-packs.js'),'utf8');
const preview={definition:{name:'<unsafe>',version:'1.0.0',packId:'own-demo',license:'INTERNAL',rightsStatement:'Own rules',rules:[]},checksum:'a'.repeat(64),tests:[],testsPassed:true};
async function setup(previewResult=preview){
 const elements={},calls=[];
 for(const id of ['packMessage','packPreview','packVersions','packFile','packImport','packRefresh','packRights','ruleSourceMode','ruleSourceSave'])elements[id]={disabled:true,files:[],checked:false,value:''};
 vm.runInNewContext(source,{document:{getElementById:id=>elements[id]},location:{assign:assert.fail},confirm:()=>true,fetch:async(url,options={})=>{
  calls.push({url,...options});const value=url==='/api/auth/me'?{csrfToken:'csrf',permissions:['SETTINGS_MANAGE']}:url==='/api/settings/rule-source'?(options.method==='PUT'?JSON.parse(options.body):{mode:'IMPORTED'}):url.endsWith('/preview')||url.endsWith('/test')?previewResult:url==='/api/settings/rule-packs'&&!options.method?[]:null;
  return {status:200,ok:true,headers:{get:()=>null},text:async()=>JSON.stringify(value)};
 }});
 await new Promise(resolve=>setImmediate(resolve));return {elements,calls};
}
test('pack preview escapes metadata and imports original JSON with CSRF without activation',async()=>{
 const {elements:e,calls}=await setup();e.packFile.files=[{size:2,text:async()=>'{}'}];await e.packFile.onchange();
 assert.match(e.packPreview.innerHTML,/&lt;unsafe&gt;/);assert.equal(e.packImport.disabled,false);await e.packImport.onclick();
 const imported=calls.find(c=>c.url==='/api/settings/rule-packs'&&c.method==='POST');assert.equal(imported.body,'{}');assert.equal(imported.headers['X-CSRF-TOKEN'],'csrf');assert.equal(calls.some(c=>c.url.endsWith('/activate')),false);
});
test('activation requires explicit rights confirmation and resets acknowledgement',async()=>{
 const {elements:e,calls}=await setup();const button={dataset:{activate:'version-id',label:'own-demo v1'},disabled:false};const event={target:{closest:()=>button}};
 await e.packVersions.onclick(event);assert.equal(calls.some(c=>c.url.endsWith('/activate')),false);assert.match(e.packMessage.textContent,/Nutzungsrechte/);
 e.packRights.checked=true;await e.packVersions.onclick(event);const call=calls.find(c=>c.url.endsWith('/activate'));assert.equal(JSON.parse(call.body).rightsConfirmed,true);assert.equal(e.packRights.checked,false);
});
test('testing a stored version invalidates any pending file import',async()=>{
 const {elements:e,calls}=await setup();e.packFile.files=[{size:2,text:async()=>'{}'}];await e.packFile.onchange();
 await e.packVersions.onclick({target:{closest:()=>({dataset:{test:'version-id'}})}});assert.equal(e.packImport.disabled,true);await e.packImport.onclick();assert.equal(calls.some(c=>c.url==='/api/settings/rule-packs'&&c.method==='POST'),false);
});
test('packs up to five MiB reach validation and larger files are rejected before reading',async()=>{
 const {elements:e,calls}=await setup();e.packFile.files=[{size:5*1024*1024,text:async()=>'{}'}];await e.packFile.onchange();assert.equal(e.packImport.disabled,false);const requests=calls.length;
 e.packFile.files=[{size:5*1024*1024+1,text:async()=>assert.fail('Oversized file must not be read')}];await e.packFile.onchange();assert.equal(calls.length,requests);assert.match(e.packMessage.textContent,/5 MB/);assert.equal(e.packImport.disabled,true);
});
test('specification reports are escaped and cannot be imported or activated',async()=>{
 const report={kind:'SPECIFICATION',schemaVersion:5,rules:2,supported:1,message:'Inspection only',configurationRequirements:['Calendar <unsafe>'],issues:[{ruleId:'<unsafe>',reason:'Unsupported'}]};
 const {elements:e,calls}=await setup(report);e.packFile.files=[{size:2,text:async()=>'{}'}];await e.packFile.onchange();
 assert.equal(e.packImport.disabled,true);assert.match(e.packPreview.innerHTML,/1 von 2/);assert.match(e.packPreview.innerHTML,/Calendar &lt;unsafe&gt;/);
 assert.doesNotMatch(e.packPreview.innerHTML,/<unsafe>/);await e.packImport.onclick();
 assert.equal(calls.some(c=>c.url==='/api/settings/rule-packs'&&c.method==='POST'),false);
});
test('rule source is loaded for the tenant and saved with CSRF',async()=>{
 const {elements:e,calls}=await setup();
 assert.equal(e.ruleSourceMode.value,'IMPORTED');assert.equal(e.ruleSourceMode.disabled,false);assert.equal(e.ruleSourceSave.disabled,false);
 e.ruleSourceMode.value='EMBEDDED';await e.ruleSourceSave.onclick();
 const saved=calls.find(c=>c.url==='/api/settings/rule-source'&&c.method==='PUT');
 assert.equal(JSON.parse(saved.body).mode,'EMBEDDED');assert.equal(saved.headers['X-CSRF-TOKEN'],'csrf');
 assert.match(e.packMessage.textContent,/Regelquelle gespeichert/);assert.equal(e.ruleSourceSave.disabled,false);
});
