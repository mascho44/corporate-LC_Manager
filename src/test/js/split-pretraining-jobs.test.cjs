const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8');
function setup(failed=false){
 const button={disabled:false},status={textContent:'',setAttribute(){}},file={name:'synthetic.pdf'},picker={files:[file],click(){}};let calls=[],opened=null;
 const context=vm.createContext({document:{createElement:()=>picker},FormData:class{append(){}},$:selector=>selector==='#openSplitPretraining'?button:status,Date,encodeURIComponent,setTimeout:callback=>callback(),json:async(url)=>{calls.push(url);return calls.length===1?{id:'job',state:'QUEUED',message:'Queued'}:failed?{id:'job',state:'FAILED',message:'OCR_TIMEOUT'}:{id:'job',state:'COMPLETED',result:{proposal:{pageCount:2},receipt:'signed'}};},openInboxSplit:async input=>{opened=input;}});
 vm.runInContext(source.slice(source.indexOf('async function openSplitPretraining('),source.indexOf('async function openInboxSplit(')),context);
 return {run:async()=>{await context.openSplitPretraining();await picker.onchange();},button,status,calls,get opened(){return opened;},file};
}
test('pretraining uploads once then polls a short status request and opens the completed proposal',async()=>{
 const state=setup();await state.run();assert.deepEqual(state.calls,['/api/training/document-types/jobs','/api/training/document-types/jobs/job']);assert.equal(state.opened.receipt,'signed');assert.equal(state.opened.file,state.file);assert.equal(state.button.disabled,false);
});
test('failed background OCR displays its reason and enables retry without opening split',async()=>{
 const state=setup(true);await state.run();assert.equal(state.status.textContent,'OCR_TIMEOUT');assert.equal(state.opened,null);assert.equal(state.button.disabled,false);
});
