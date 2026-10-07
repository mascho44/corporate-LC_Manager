const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');

function fixture(responses,tenantCode){
  class Element{
    constructor(tag){this.tag=tag;this.children=[];this.inputs=[];this.disabled=false;}
    append(...items){this.children.push(...items);}
    querySelectorAll(selector){return selector==='input'?this.inputs:[];}
    querySelector(){return this.inputs[0];}
    set innerHTML(value){this.html=value;this.inputs=[{required:true,disabled:false,value:'123456',focus(){}}];}
  }
  const button=new Element('button'),error=new Element('div'),form=new Element('form');
  const inputs=[{required:true,value:'user'},{required:true,value:'password'}];
  const labels=inputs.map(input=>{const label=new Element('label');label.inputs=[input];return label;});
  form.elements={};form.children=[...labels,error,button];form.querySelector=()=>button;
  form.querySelectorAll=()=>labels;
  form.insertBefore=(item)=>{form.children.push(item);if(item.inputs.length)form.elements.code=item.inputs[0];};
  form.replaceChildren=()=>{form.children=[];};
  const calls=[],redirects=[];
  const context={document:{querySelector:selector=>selector==='#loginForm'?form:error,createElement:tag=>new Element(tag)},
    fetch:async(url,options)=>{calls.push({url,options});const body=responses.shift();return {ok:!body.error,json:async()=>body};},
    FormData:class{entries(){return [['username','user'],['password','password'],['tenantCode',tenantCode||'']];}},location:{replace:url=>redirects.push(url)}};
  vm.runInNewContext(fs.readFileSync('src/main/resources/static/login.js','utf8'),context);
  return {form,inputs,labels,button,error,calls,redirects,submit:()=>form.onsubmit({preventDefault(){}})};
}
test('admin enrollment disables hidden required credentials and preserves recovery-code acknowledgement',async()=>{
  const f=fixture([{requiresTotpSetup:true,secret:'secret',qrCodeDataUrl:'data:image/png;base64,test'},{recoveryCodes:['one','two']}]);
  await f.submit();
  assert.ok(f.inputs.every(input=>input.disabled&&!input.required&&input.value===''));
  assert.ok(f.labels.every(label=>label.hidden));
  assert.equal(f.redirects.length,0);
  await f.submit();
  assert.equal(f.calls[1].url,'/api/auth/login/totp');
  assert.deepEqual(JSON.parse(f.calls[1].options.body),{code:'123456'});
  assert.equal(f.redirects.length,0);
  assert.equal(f.form.children.find(child=>child.tag==='pre').textContent,'one\ntwo');
  f.form.children.find(child=>child.tag==='button').onclick();assert.deepEqual(f.redirects,['/']);
});
test('existing TOTP login redirects only after successful code verification',async()=>{
  const f=fixture([{requiresTotp:true},{username:'user'}]);await f.submit();assert.equal(f.redirects.length,0);
  await f.submit();assert.deepEqual(f.redirects,['/']);
});
test('throttled login displays error and allows later retry',async()=>{
  const f=fixture([{error:'Bitte später erneut versuchen.'}]);await f.submit();
  assert.equal(f.error.textContent,'Bitte später erneut versuchen.');assert.equal(f.button.disabled,false);assert.equal(f.redirects.length,0);
});
test('tenant code is sent only at password stage, not in the second-factor request',async()=>{const f=fixture([{requiresTotp:true},{username:'user'}],'synthetic');await f.submit();assert.equal(JSON.parse(f.calls[0].options.body).tenantCode,'synthetic');await f.submit();assert.deepEqual(JSON.parse(f.calls[1].options.body),{code:'123456'});});
