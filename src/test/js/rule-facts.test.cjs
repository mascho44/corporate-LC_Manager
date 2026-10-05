const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/rule-facts.js'),'utf8');
const settle=()=>new Promise(resolve=>setImmediate(resolve));
function setup(allowed=true){
 const calls=[],dialogs={};let click;const close={},context={},submit={disabled:false};
 const form={elements:{},querySelector:()=>submit,querySelectorAll:()=>Object.values(form.elements)};
 const dialog={open:false,showModal(){this.open=true;},close(){this.open=false;},querySelector(selector){return selector==='form'?form:selector==='[data-context]'?context:{textContent:''};},querySelectorAll:()=>[close]};
 Object.defineProperty(dialog,'innerHTML',{set(html){this.html=html;form.elements={};for(const m of html.matchAll(/<(textarea|select) name="([^"]+)"[^>]*>([\s\S]*?)<\/\1>/g)){const selected=m[1]==='select'?m[3].match(/<option value="([^"]+)" selected/):null;form.elements[m[2]]={value:m[1]==='textarea'?m[3]:selected?.[1]||''};}}});
 const document={getElementById:id=>dialogs[id],createElement:()=>dialog,body:{append:element=>dialogs[element.id]=element},addEventListener:(type,handler)=>click=handler};
 const globals={document,activeLc:{id:'test-lc'},can:()=>allowed,esc:value=>String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;'),alert:assert.fail,show:async id=>calls.push({refresh:id}),json:async(url,options)=>{
  calls.push({url,...options});return url.includes('/documents/')?{DOCUMENT_ISSUER:'<unsafe>'}:{};
 }};
 vm.runInNewContext(source,globals);
 return {calls,dialog,form,context,close,click:()=>click({target:{closest:()=>({dataset:{ruleFacts:'test-document'}})}})};
}
test('rule facts escape values and preserve unknown context instead of defaulting false',async()=>{
 const f=setup();f.click();await settle();assert.match(f.dialog.html,/&lt;unsafe&gt;/);
 await f.context.onclick();assert.equal(f.form.elements.LC_TRANSFERRED.value,'');assert.equal(f.form.elements.LC_RULE_STANDARD.value,'');
});
test('rule facts save to captured document and refresh its LC',async()=>{
 const f=setup();f.click();await settle();f.form.elements.DOCUMENT_ISSUER.value='Synthetic issuer';
 await f.form.onsubmit({preventDefault(){}});
 const saved=f.calls.find(c=>c.method==='PUT');assert.equal(saved.url,'/api/lcs/test-lc/documents/test-document/rule-facts');
 assert.equal(JSON.parse(saved.body).DOCUMENT_ISSUER,'Synthetic issuer');assert.equal(JSON.parse(saved.body).DOCUMENT_RECIPIENT,null);
 assert.equal(f.dialog.open,false);assert.equal(f.calls.at(-1).refresh,'test-lc');
});
test('read-only user cannot submit supplementary facts',async()=>{
 const f=setup(false);f.click();await settle();assert.doesNotMatch(f.dialog.html,/type="submit"/);
 assert.ok(Object.values(f.form.elements).every(input=>input.disabled));
 await f.form.onsubmit({preventDefault(){}});assert.equal(f.calls.some(c=>c.method==='PUT'),false);
});
