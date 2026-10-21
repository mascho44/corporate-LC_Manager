const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/finding-evidence.js'),'utf8');
test('evidence dialog confirms the exact displayed finding',async()=>{
 let action,submission,query;
 const button={dataset:{findingEvidence:'TEST',findingDocument:'invoice.pdf',findingFingerprint:'a'.repeat(64)}};
 const close={};const actionButton={dataset:{evidenceDecision:'ACCEPTED'}};
 const dialog={open:true,showModal(){},close(){this.open=false;},querySelector:()=>close,querySelectorAll:()=>[actionButton]};
 const document={querySelector:()=>dialog,addEventListener:()=>{}};
 const finding={code:'TEST',documentName:'invoice.pdf',reviewFingerprint:'a'.repeat(64)};
 const context=vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),document,activeLc:{id:'lc'},URLSearchParams,esc:x=>String(x||''),label:x=>x,renderRuleMetadata:()=>'',can:()=>true,alert:assert.fail,
 json:async url=>{query=url;return {finding,location:{status:'NO_DOCUMENT',method:'NONE',pages:[]}};},decideCheck:async(...args)=>{submission=args;}});
 vm.runInContext(source,context);await context.openFindingEvidence(button);await actionButton.onclick();
 assert.match(query,/reviewFingerprint=a{64}/);assert.equal(submission[3],finding.reviewFingerprint);
});
test('a stored evidence mark is read from JSON and rejected when it is incomplete', () => {
 const fs = require('node:fs'), vm = require('node:vm');
 const source = fs.readFileSync('src/main/resources/static/finding-evidence.js', 'utf8');
 const code = source.slice(source.indexOf('function parseMark('), source.indexOf('async function renderFindingPdf('));
 const context = vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),}); vm.runInContext(code, context);
 const mark = context.parseMark('{"documentId":"d1","page":2,"x":0.1,"y":0.2,"w":0.3,"h":0.05}');
 assert.equal(mark.page, 2); assert.equal(mark.w, 0.3);
 assert.equal(context.parseMark(null), null); assert.equal(context.parseMark('{"page":2}'), null); assert.equal(context.parseMark('kein json'), null);
});
