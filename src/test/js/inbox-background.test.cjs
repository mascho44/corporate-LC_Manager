const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
test('automatic split completion signals refresh without overwriting ongoing assignments',async()=>{const f=fixture([{id:'new-part',automaticallySplit:true,extractionStatus:'EXTRACTED'}]);f.list.innerHTML='user draft';vm.runInContext('inboxEditedItems.add("other")',f.context);await f.context.loadInbox(true);assert.equal(f.list.innerHTML,'user draft');assert.match(f.nodes.message.textContent,/Automatically split documents are available/);});
function fixture(items){
 const timers=[],nodes={},articles=[],created=[],calls=[];const list={prepend(){},innerHTML:'',textContent:'',querySelectorAll(selector){return selector==='[data-inbox-id]'?articles:[];},querySelector(selector){return nodes[selector]||null;},contains(){return false;}};
const context=vm.createContext({document:{addEventListener(){},activeElement:null,createElement:tag=>{const node={tag,dataset:{},append(){},after(){},addEventListener(){},replaceChildren(){}};created.push(node);return node;}},$:selector=>selector==='#inboxList'?list:selector==='#inboxSection'?{classList:{contains:()=>false}}:nodes.message??={textContent:''},clearTimeout(){},setTimeout(fn,delay){timers.push([fn,delay]);return timers.length;},
  confirmAction:async()=>true,json:async(url,options)=>{calls.push({url,options});return url==='/api/inbox'?items:[];},renderInboxItem:item=>`item:${item.id}`,esc:String,extractionLabel:status=>status,can:()=>true});
 const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
 vm.runInContext(source.slice(source.indexOf('let inboxRefreshTimer='),source.indexOf('let appNavigate=')),context);
 return {context,list,timers,nodes,articles,created,calls};
}
test('saved review restores explicit blanks, numbered copies and confirmed status',()=>{
 const f=fixture([]),status={textContent:''},quality={textContent:''};
 const elements=Object.fromEntries(['lcId','documentType','copyNumber','documentDate','spatialProfile','metadataReference','metadataNumber','metadataAmount','metadataCurrency'].map(name=>[name,{value:'automatic'}]));
 const form={elements,querySelector:()=>status,querySelectorAll:()=>[quality]};
 f.context.restoreInboxReview(form,{metadataReviewJson:JSON.stringify({lcId:null,documentType:'PACKING_LIST',copyNumber:-2,documentDate:null,metadata:{reference:null,documentNumber:'PL42',amount:0,currency:'EUR'},profile:'Bank',confirmed:true})});
 assert.equal(elements.metadataReference.value,'');assert.equal(elements.lcId.value,'');assert.equal(elements.documentDate.value,'');assert.equal(elements.copyNumber.value,-2);assert.equal(elements.metadataAmount.value,0);assert.equal(elements.metadataNumber.value,'PL42');assert.match(quality.textContent,/bestätigt/);
 f.context.restoreInboxReview(form,{metadataReviewJson:'invalid'});assert.match(status.textContent,/konnte nicht geladen/);
});
test('saving a review does not train and persists assignment and profile',async()=>{
 const f=fixture([]);f.context.readDocumentCopy=value=>Number(value);
 const elements=Object.fromEntries(Object.entries({lcId:'lc',documentType:'PACKING_LIST',copyNumber:'2',documentDate:'2026-10-08',spatialProfile:' Bank ',metadataReference:'LC42',metadataNumber:'PL42',metadataAmount:'0',metadataCurrency:'EUR'}).map(([key,value])=>[key,{value}]));
 await f.context.saveInboxReview({elements,dataset:{inboxAttach:'item'}});
 assert.equal(f.calls[0].url,'/api/inbox/item/metadata-review');const payload=JSON.parse(f.calls[0].options.body);assert.equal(payload.confirmed,false);assert.equal(payload.profile,'Bank');assert.equal(payload.lcId,'lc');assert.equal(payload.metadata.amount,0);
});
test('attachment preserves per-document corrections and explicit empty metadata',()=>{
 const f=fixture([]);f.context.readDocumentCopy=value=>value===''?null:Number(value);
 const elements=Object.fromEntries(Object.entries({documentType:'PACKING_LIST',documentDate:'2026-08-25',copyNumber:'2',metadataReference:' LC123 ',metadataNumber:' INV42 ',metadataAmount:'42.50',metadataCurrency:'EUR'}).map(([key,value])=>[key,{value}]));
 const value=f.context.inboxAttachment({elements},'target');assert.equal(value.lcId,'target');assert.equal(value.copyNumber,2);assert.equal(value.metadata.reference,'LC123');assert.equal(value.metadata.amount,42.5);
 elements.metadataReference.value='';elements.metadataAmount.value='';assert.equal(f.context.inboxAttachment({elements},'target').metadata.reference,null);assert.equal(f.context.inboxAttachment({elements},'target').metadata.amount,null);
});
test('learned anchor proposals are accepted and only evidenced fields are overwritten',()=>{
 const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
 assert.ok(source.includes("['LEARNED_REVIEW','ANCHOR_REVIEW'].includes(proposal.status)"));
 assert.ok(source.includes("Object.hasOwn(proposal.evidence||{},key)"));
 assert.ok(source.includes('Textanker-Fundstellen aus dem neuen Beleg'));
});
test('selected files survive background polling',async()=>{
 const f=fixture([{id:'test',extractionStatus:'EXTRACTED'}]);f.list.innerHTML='selected file';f.nodes['[data-inbox-select]:checked']={checked:true};await f.context.loadInbox(true);assert.equal(f.list.innerHTML,'selected file');
});
test('bulk selection is permission gated and counts selected files',()=>{
 const f=fixture([]);assert.match(f.context.inboxBulkToolbar(),/Alle angezeigten auswählen/);f.context.can=()=>false;assert.equal(f.context.inboxBulkToolbar(),'');
 const all={},button={},boxes=[{checked:true},{checked:false}];f.context.updateInboxSelection({querySelectorAll:()=>boxes,querySelector:s=>s.includes('select-all')?all:button});assert.equal(all.indeterminate,true);assert.equal(button.disabled,false);assert.match(button.textContent,/\(1\)/);
});
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
test('partial recognition reports failed pages and allows retry with numeric progress',()=>{
 const f=fixture([]);
 const pages=[{page:1,status:'OCR_EXTRACTED'},{page:2,status:'OCR_TIMEOUT'}];
 assert.match(f.context.inboxStatusHtml({id:'test',extractionStatus:'OCR_PARTIAL',recognitionPages:pages}),/Seite 2: OCR_TIMEOUT/);
 assert.match(f.context.inboxStatusHtml({id:'test',extractionStatus:'OCR_PARTIAL',recognitionPages:pages}),/data-inbox-retry/);
 assert.match(f.context.inboxStatusHtml({id:'test',extractionStatus:'PROCESSING',recognitionPages:pages}),/value="1" max="2"/);
});
test('accepted scan correction is visible but the original stays unchanged',()=>{
 const f=fixture([]);
 assert.match(f.context.inboxStatusHtml({extractionStatus:'OCR_EXTRACTED',recognitionPages:[{page:1,status:'OCR_EXTRACTED',correctionDegrees:90}]}),/90° korrigiert · Original unverändert/);
});

test('existing PDFs offer a confirmed explicit automatic split using the protected POST action',async()=>{const f=fixture([{id:'existing',contentType:'application/pdf',extractionStatus:'EXTRACTED'}]);f.articles.push({dataset:{inboxId:'existing'},querySelector:()=>({after(){}}),addEventListener(){},querySelectorAll:()=>[]});await f.context.loadInbox();let opened=false;f.context.openInboxSplit=async item=>{opened=item.id;};const button=f.created.find(n=>n.textContent==='Split automatically');assert.ok(button);f.context.confirmAction=async()=>false;await button.onclick();assert.equal(f.calls.some(c=>c.url.endsWith('/auto-split')),false);f.context.confirmAction=async()=>true;await button.onclick();assert.equal(f.calls.find(c=>c.url.endsWith('/auto-split')).options.method,'POST');assert.match(f.nodes.message.textContent,/No safe automatic split available/);assert.equal(opened,'existing');assert.match(button.className,/inbox-split-action/);});
