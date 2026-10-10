const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/guarantee-messages.js'),'utf8');
function load(){const context=vm.createContext({});context.globalThis=context;vm.runInContext(source,context);return context.LcGuaranteeMessages;}
test('only dossiers of kind GUARANTEE get the badge',()=>{
 const m=load();assert.equal(m.isGuarantee({instrumentType:'GUARANTEE'}),true);assert.equal(m.isGuarantee({instrumentType:'LC'}),false);assert.equal(m.isGuarantee(null),false);
 assert.match(m.badge(),/Garantie/);
});
test('message text is escaped before it reaches the page',()=>{
 const m=load();
 const html=m.messageHtml({messageType:'MT799',reference:'<b>R</b>',relatedReference:'LC"1',importedAt:'2026-10-10T09:30:00',narrative:'<script>alert(1)</script> & more'});
 assert.equal(html.includes('<script'),false);assert.equal(html.includes('<b>R'),false);
 assert.match(html,/&lt;script&gt;/);assert.match(html,/LC&quot;1/);assert.match(html,/2026-10-10 09:30/);
});
test('the panel is empty without messages and counts them otherwise',()=>{
 const m=load();assert.equal(m.panelHtml([]),'');assert.equal(m.panelHtml(null),'');
 assert.match(m.panelHtml([{messageType:'MT199',reference:'A',narrative:'x'},{messageType:'MT799',reference:'B',narrative:'y'}]),/Bankmitteilungen \(2\)/);
});
test('page loads the script',()=>{assert.match(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/index.html'),'utf8'),/guarantee-messages\.js/);});
