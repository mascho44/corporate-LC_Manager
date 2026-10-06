const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
function fixture(items){
 const timers=[],nodes={};const list={innerHTML:'',textContent:'',querySelectorAll(){return[];},querySelector(selector){return nodes[selector]||null;},contains(){return false;}};
 const context=vm.createContext({document:{activeElement:null},$:selector=>selector==='#inboxList'?list:selector==='#inboxSection'?{classList:{contains:()=>false}}:nodes.message??={textContent:''},clearTimeout(){},setTimeout(fn,delay){timers.push([fn,delay]);return timers.length;},
  json:async url=>url==='/api/inbox'?items:[],renderInboxItem:item=>`item:${item.id}`,esc:String,extractionLabel:status=>status,can:()=>true});
 const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
 vm.runInContext(source.slice(source.indexOf('let inboxRefreshTimer='),source.indexOf('let appNavigate=')),context);
 return {context,list,timers,nodes};
}
test('queued items poll and show background status, completed items stop polling',async()=>{
 const f=fixture([{id:'test',extractionStatus:'QUEUED'}]);await f.context.loadInbox();assert.match(f.list.innerHTML,/item:test/);assert.equal(f.timers[0][1],4000);
 assert.match(f.context.inboxStatusHtml({id:'test',extractionStatus:'QUEUED'}),/Datei ist gespeichert/);
 const done=fixture([{id:'test',extractionStatus:'EXTRACTED'}]);await done.context.loadInbox();assert.equal(done.timers.length,0);
});
test('polling does not overwrite edited assignments and opens completed controls',async()=>{
 const f=fixture([{id:'test',extractionStatus:'EXTRACTED'}]);f.list.innerHTML='user draft';
 const status={innerHTML:''},control={disabled:true},article={dataset:{pending:'true'},querySelectorAll:()=>[control]};
 f.nodes['[data-inbox-status="test"]']=status;f.nodes['[data-inbox-id="test"]']=article;
 vm.runInContext('inboxEditedItems.add("other")',f.context);await f.context.loadInbox(true);
 assert.equal(f.list.innerHTML,'user draft');assert.match(status.innerHTML,/EXTRACTED/);assert.equal(control.disabled,false);
});
test('timed-out items can retry an existing file without uploading again',()=>{
 const f=fixture([]);assert.match(f.context.inboxStatusHtml({id:'test',extractionStatus:'OCR_TIMEOUT'}),/data-inbox-retry="test"/);
 assert.doesNotMatch(f.context.inboxStatusHtml({id:'test',extractionStatus:'PROCESSING'}),/data-inbox-retry/);
});
