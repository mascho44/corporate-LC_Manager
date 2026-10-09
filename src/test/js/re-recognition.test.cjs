const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/re-recognition.js','utf8');
function setup(responses,docButtons){
 function node(tag){return{tag,children:[],textContent:'',className:'',dataset:{},attributes:{},disabled:false,
  append(...i){this.children.push(...i);},remove(){this.removed=true;},addEventListener(n,f){this['on'+n]=f;},setAttribute(k,v){this.attributes[k]=v;},showModal(){this.open=true;},close(){this.open=false;}};}
 const body=node('body'),calls=[];let listener;
 const context=vm.createContext({activeLc:{id:'lc1'},csrfToken:'tok',load:async()=>{},show:async()=>{},
  document:{createElement:node,body,addEventListener:(n,f)=>{listener=f;},querySelectorAll:()=>docButtons},
  fetch:async(url,options)=>{calls.push({url,body:options.body,csrf:options.headers['X-CSRF-TOKEN']});const r=responses.shift();return {ok:r.ok!==false,json:async()=>r.body};}});
 vm.runInContext(source,context);
 const texts=()=>{const out=[];const walk=n=>{if(n.textContent)out.push(n.textContent);n.children?.forEach(walk);};walk(body);return out.join('\n');};
 const all=n=>{const out=[n];n.children?.forEach(c=>out.push(...all(c)));return out;};
 return{click:target=>listener({target:{closest:sel=>target[sel]?target.element:null}}),calls,texts,body,all};
}
const preview={documentId:'d1',filename:'scan.pdf',token:'abc',anyApplied:true,changes:[{key:'documentDate',label:'Dokumentdatum',before:null,after:'2026-07-21',appliesOnConfirm:true,note:'wird ergänzt'},{key:'type',label:'Dokumenttyp',before:'Other',after:'Commercial Invoice',appliesOnConfirm:false,note:'Nur ein Vorschlag, der Typ bleibt unverändert.'}]};
test('single document: shows before/after, applies only after confirmation and sends the CSRF token',async()=>{
 const f=setup([{body:preview},{body:{id:'d1'}}]);
 const element={dataset:{documentRerecognize:'d1',documentName:'scan.pdf'}};
 await f.click({'[data-document-rerecognize]':true,element});
 assert.equal(f.calls.length,1);assert.equal(f.calls[0].url,'/api/lcs/lc1/documents/d1/re-recognition');assert.equal(f.calls[0].csrf,'tok');
 assert.match(f.texts(),/Dokumentdatum/);assert.match(f.texts(),/wird ergänzt/);assert.match(f.texts(),/Nur ein Vorschlag/);assert.match(f.texts(),/1 von 1 Dokumenten mit Änderungen/);
 const apply=f.all(f.body).find(n=>n.tag==='button'&&n.textContent==='Übernehmen');assert.ok(apply);assert.equal(f.calls.length,1);
 await apply.onclick();assert.equal(f.calls.length,2);assert.equal(f.calls[1].url,'/api/lcs/lc1/documents/d1/re-recognition/apply');assert.deepEqual(JSON.parse(f.calls[1].body),{token:'abc'});assert.equal(apply.textContent,'Übernommen');
});
test('whole dossier: previews every document one after another, unchanged ones say so, errors stay per document',async()=>{
 const f=setup([{body:{...preview,filename:'a.pdf'}},{body:{documentId:'d2',filename:'b.pdf',token:'t',anyApplied:false,changes:[]}},{ok:false,body:{error:'Das Dokument hat mehr als 10 Seiten.'}}],
  [{dataset:{documentRerecognize:'d1',documentName:'a.pdf'}},{dataset:{documentRerecognize:'d2',documentName:'b.pdf'}},{dataset:{documentRerecognize:'d3',documentName:'c.pdf'}}]);
 await f.click({'[data-document-rerecognize-all]':true,element:{}});
 assert.deepEqual(f.calls.map(c=>c.url),['/api/lcs/lc1/documents/d1/re-recognition','/api/lcs/lc1/documents/d2/re-recognition','/api/lcs/lc1/documents/d3/re-recognition']);
 assert.match(f.texts(),/Keine Änderung/);assert.match(f.texts(),/c\.pdf: Das Dokument hat mehr als 10 Seiten/);assert.match(f.texts(),/1 von 3 Dokumenten mit Änderungen/);
});
