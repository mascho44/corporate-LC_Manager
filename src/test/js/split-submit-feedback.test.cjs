const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8');
const start=source.indexOf('form.onsubmit=async event=>',source.indexOf('async function openInboxSplit('));
const end=source.indexOf('\n        };\n    }catch',start);
function setup(ranges,fail=false){
 const error={textContent:'',focus(){}},status={textContent:''},submit={textContent:'Aufteilung bestätigen',disabled:false},message={textContent:''};
 let requests=0,closed=false;
 const rows={children:ranges.map(([from,to])=>({querySelector(selector){return {value:selector.includes('fromPage')?String(from):selector.includes('toPage')?String(to):selector.includes('documentType')?'OTHER':'',focus(){}};}}))};
 const context=vm.createContext({rows,submitStatus:status,proposal:{pageCount:4},item:{id:'synthetic'},form:{querySelectorAll(){return [];},querySelector(){return submit;}},dialog:{querySelector(){return error;},close(){closed=true;}},readDocumentCopy:()=>null,json:async()=>{requests++;if(fail)throw Error('HTTP 503');return [{},{}];},$:()=>message,loadInbox:async()=>{}});
 vm.runInContext('handler='+source.slice(start+'form.onsubmit='.length,end)+'};',context);
 return {run:()=>context.handler({preventDefault(){}}),error,status,submit,get requests(){return requests;},get closed(){return closed;}};
}
test('invalid range click visibly explains why nothing was imported',async()=>{
 const state=setup([[1,2],[4,4]]);await state.run();
 assert.equal(state.requests,0);assert.match(state.error.textContent,/Bereich 2.*Seite 3/);assert.equal(state.status.textContent,'Nicht übernommen.');assert.equal(state.submit.disabled,false);
});
test('failed request restores submit button and shows the server error',async()=>{
 const state=setup([[1,2],[3,4]],true);await state.run();
 assert.equal(state.requests,1);assert.equal(state.submit.disabled,false);assert.equal(state.submit.textContent,'Aufteilung bestätigen');assert.equal(state.error.textContent,'HTTP 503');assert.equal(state.status.textContent,'Übernahme fehlgeschlagen.');assert.equal(state.closed,false);
});
test('successful request closes the dialog only after import',async()=>{
 const state=setup([[1,2],[3,4]]);await state.run();assert.equal(state.requests,1);assert.equal(state.closed,true);
});
