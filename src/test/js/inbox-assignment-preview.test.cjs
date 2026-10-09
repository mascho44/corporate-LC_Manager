const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8');
const context=vm.createContext({esc:value=>String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;')});
vm.runInContext(source.slice(source.indexOf('function inboxAssignmentPreview('),source.indexOf('function documentCopyLabel(')),context);
test('PDF and image previews are lazy, same-origin and escape names',()=>{
 const pdf=context.inboxAssignmentPreview({id:'synthetic',contentType:'application/pdf',originalFilename:'<script>.pdf'});
 assert.match(pdf,/<img data-preview-src="\/api\/inbox\/synthetic\/pages\/1\/preview\?enlarged=true"/);assert.doesNotMatch(pdf,/<iframe|<img[^>]*\ssrc=/);assert.doesNotMatch(pdf,/<script>/);assert.match(pdf,/Original separat öffnen/);
 const image=context.inboxAssignmentPreview({id:'synthetic',contentType:'image/png',originalFilename:'scan.png'});assert.match(image,/<img data-preview-src=/);
});
test('active or unsupported formats cannot be embedded',()=>{
 const html=context.inboxAssignmentPreview({id:'synthetic',contentType:'text/html',originalFilename:'unsafe.html'});assert.doesNotMatch(html,/<iframe|<img/);assert.match(html,/Original separat öffnen/);
});
test('opening preview loads once and protects the item from polling replacement',()=>{
 let handler,src,count=0;const edited=new Set();const ctx=vm.createContext({document:{querySelector:()=>null,addEventListener:(event,fn,capture)=>{assert.equal(event,'toggle');assert.equal(capture,true);handler=fn;}},inboxEditedItems:edited});
 vm.runInContext(source.slice(source.indexOf("document.addEventListener('toggle'"),source.indexOf('function ensureInternalPackNav(')),ctx);
 const media={dataset:{previewSrc:'/api/inbox/synthetic/content'},hasAttribute:()=>!!src,set src(value){src=value;count++;}};
 const preview={matches:()=>true,open:false,querySelector:()=>media,closest:()=>({dataset:{inboxId:'synthetic'}})};
 handler({target:preview});assert.equal(count,0);preview.open=true;handler({target:preview});handler({target:preview});assert.equal(count,1);assert.equal(src,'/api/inbox/synthetic/content');assert.ok(edited.has('synthetic'));
});
