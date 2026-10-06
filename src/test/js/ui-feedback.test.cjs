const test=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
function fixture(){
 const dialogs=[],requests=[];let focused=0;
 function element(){return {attrs:{},events:{},textContent:'',setAttribute(k,v){this.attrs[k]=v;},removeAttribute(k){delete this.attrs[k];},addEventListener(k,v){this.events[k]=v;},showModal(){this.open=true;},close(){this.open=false;},remove(){this.removed=true;},querySelector(k){return this.children[k]??=element();},children:{}};}
 class Xhr{
  constructor(){this.upload=element();requests.push(this);this.headers={};}
  open(method,url){this.method=method;this.url=url;}
  setRequestHeader(k,v){this.headers[k]=v;}
  send(body){this.body=body;}
  getResponseHeader(){return 'application/json';}
  abort(){this.onabort();}
 }
 const context=vm.createContext({document:{activeElement:{focus(){focused++;}},createElement:element,body:{append(d){dialogs.push(d);}}},XMLHttpRequest:Xhr,Response,Headers,DOMException,Promise});
 vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/ui-feedback.js'),'utf8'),context);
 return {context,dialogs,requests,focused:()=>focused};
}
test('destructive confirmation uses text, resolves Ja/Nein and restores focus',async()=>{
 const f=fixture();const result=f.context.confirmAction('<script>unsafe</script>');const d=f.dialogs[0];
 assert.equal(d.querySelector('p').textContent,'<script>unsafe</script>');
 d.querySelector('.feedback-yes').onclick();assert.equal(await result,true);assert.equal(d.removed,true);assert.equal(f.focused(),1);
 const no=f.context.confirmAction('Löschen?');f.dialogs[1].querySelector('.feedback-no').onclick();assert.equal(await no,false);
});
test('Escape cancels deletion without confirming',async()=>{
 const f=fixture(),result=f.context.confirmAction('Löschen?');let prevented=false;
 f.dialogs[0].events.cancel({preventDefault(){prevented=true;}});
 assert.equal(await result,false);assert.equal(prevented,true);
});
test('upload reports real transfer progress then indeterminate processing',async()=>{
 const f=fixture(),result=f.context.uploadWithProgress('/api/training/preview',{method:'POST',headers:{'X-CSRF-TOKEN':'token'},body:'test-body'}),xhr=f.requests[0],d=f.dialogs[0];
 xhr.upload.events.progress({lengthComputable:true,loaded:25,total:100});
 assert.equal(d.querySelector('progress').value,25);assert.match(d.querySelector('.processing-stage').textContent,/25 %/);
 xhr.upload.events.load();assert.equal(d.querySelector('progress').attrs['aria-label'],'Verarbeitung läuft');assert.match(d.querySelector('.processing-stage').textContent,/Upload abgeschlossen/);
 assert.equal(xhr.headers['x-csrf-token'],'token');assert.equal(xhr.body,'test-body');
 xhr.status=200;xhr.statusText='OK';xhr.responseText='{"ok":true}';xhr.onload();
 assert.deepEqual(await (await result).json(),{ok:true});assert.equal(d.removed,true);
});
test('network failure and HTTP error both close waiting dialog',async()=>{
 const f=fixture(),network=f.context.uploadWithProgress('/api/inbox');f.requests[0].onerror();
 await assert.rejects(network,/Verbindung/);assert.equal(f.dialogs[0].removed,true);
 const http=f.context.uploadWithProgress('/api/inbox'),xhr=f.requests[1];xhr.status=400;xhr.statusText='Bad Request';xhr.responseText='{"error":"Invalid PDF"}';xhr.onload();
 assert.equal((await http).status,400);assert.equal(f.dialogs[1].removed,true);
});
