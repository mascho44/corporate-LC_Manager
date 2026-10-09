const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8');
test('document drafts and completeness target content sections, not navigation buttons',()=>{
 assert.doesNotMatch(source,/#detail \[data-section="(?:documents|overview)"\]/);
 assert.match(source,/#detail \.dossier-section\[data-section="documents"\]/);
});
test('refresh replaces deadlines without duplicating tabs and retains selection',()=>{
 const start=source.indexOf("    const dossierContent=$('#detail .dossier-content'),dossierNav=$('#detail .dossier-nav');",source.indexOf('const showWithOperationalCockpit'));
 const end=source.indexOf("    if(examinationWorkspacePanel",start);
 const nodes=()=>{const items=[{remove(){items.splice(items.indexOf(this),1);}}];return {items,querySelectorAll(){return [...items];},querySelector(){return {};},append(value){value.remove=()=>items.splice(items.indexOf(value),1);items.push(value);}};};
 const content=nodes(),nav=nodes();let activations=0;
 const context=vm.createContext({$:selector=>selector.includes('content')?content:nav,document:{createElement:()=>({dataset:{}})},deadlineSection:{},activateDossierSection:name=>{assert.equal(name,'deadlines');activations++;}});
 for(let i=0;i<3;i++)vm.runInContext('{'+source.slice(start,end)+'}',context);
 assert.equal(content.items.length,1);assert.equal(nav.items.length,1);assert.equal(nav.items[0].textContent,'Fristen');assert.equal(activations,3);
});
test('dossier tabs do not stretch or wrap on mobile',()=>{
 const css=fs.readFileSync('src/main/resources/static/styles.css','utf8');
 assert.match(css,/body \.dossier-nav\{[^}]*align-items:center;[^}]*flex-wrap:nowrap/);
 assert.match(css,/body \.dossier-nav>button\{[^}]*align-self:center;[^}]*white-space:nowrap/);
});
