const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
const source=fs.readFileSync(path.join(root,'workflows.js'),'utf8');
function load(){const context=vm.createContext({});context.globalThis=context;vm.runInContext(source,context);return context.LcWorkflows;}
const wf={id:'w1',templateTitle:'Neue Akte prüfen',status:'RUNNING',startedBy:'boss',steps:[
 {no:1,title:'Erfassen',state:'DONE',completedBy:'ben',fourEyes:false},
 {no:2,title:'Prüfen',state:'ACTIVE',assignedTo:null,fourEyes:false},
 {no:3,title:'Freigeben',state:'PENDING',fourEyes:true}]};
test('steps show state by symbol and text, owner and four-eyes marker',()=>{
 const html=load().workflowHtml(wf,true);
 assert.match(html,/✓.*erledigt.*Erfassen.*ben/s);assert.match(html,/●.*aktuell.*Prüfen.*in der Gruppeninbox/s);assert.match(html,/○.*wartet.*Freigeben.*Vier-Augen/s);
 assert.match(html,/data-workflow-cancel="w1"/);
});
test('cancel button only for running workflows and permitted users',()=>{
 const w=load();
 assert.equal(w.workflowHtml(wf,false).includes('data-workflow-cancel'),false);
 assert.equal(w.workflowHtml({...wf,status:'DONE'},true).includes('data-workflow-cancel'),false);
});
test('all dynamic text is escaped',()=>{
 const html=load().workflowHtml({...wf,templateTitle:'<img src=x onerror=1>',startedBy:'"><b>',steps:[{...wf.steps[0],title:'<i>x</i>',completedBy:'a&b'}]},true);
 assert.equal(html.includes('<img'),false);assert.equal(html.includes('<i>x'),false);assert.equal(html.includes('"><b>'),false);assert.match(html,/a&amp;b/);
});
test('panel is empty without workflows; page loads the script',()=>{
 assert.equal(load().panelHtml([],true),'');assert.equal(load().panelHtml(null,true),'');
 assert.match(load().panelHtml([wf],true),/class="workflows"/);
 assert.match(fs.readFileSync(path.join(root,'index.html'),'utf8'),/workflows\.js/);
});
