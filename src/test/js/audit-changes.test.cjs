const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
function fixture(translations){const context=vm.createContext({LcI18n:translations?{t:key=>translations[key]||key}:undefined});vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/audit-changes.js'),'utf8'),context);return context.LcAuditChanges;}
test('administration snapshots display before/after fields and highlight changed values',()=>{
 const api=fixture(),html=api.render({entityType:'USER',previousValue:JSON.stringify({username:'Synthetic',role:'EDITOR',active:true}),newValue:JSON.stringify({username:'Synthetic',role:'VIEWER',active:false})});
 assert.match(html,/<table/);assert.match(html,/Before/);assert.match(html,/After/);assert.match(html,/EDITOR/);assert.match(html,/VIEWER/);assert.equal((html.match(/class="audit-field-changed"/g)||[]).length,2);
});
test('untrusted names and permission strings cannot inject markup',()=>{
 const html=fixture().render({entityType:'ROLE',newValue:JSON.stringify({name:'<img src=x onerror=alert(1)>',permissions:['<script>bad</script>'],passwordHash:'should-not-render'})});
 assert.doesNotMatch(html,/<img|<script>|should-not-render/);assert.match(html,/&lt;img/);assert.match(html,/&lt;script/);
});
test('creation/deletion and malformed or legacy snapshots remain readable',()=>{
 const api=fixture();assert.equal(api.render({}), '');assert.match(api.render({entityType:'USER',previousValue:JSON.stringify({username:'Synthetic'}),newValue:null}),/Synthetic/);
 const html=api.render({entityType:'ROLE',previousValue:'not JSON <script>',newValue:'{"name":'});assert.doesNotMatch(html,/<table|<script>/);assert.match(html,/not JSON &lt;script&gt;/);
 assert.match(api.render({entityType:'LETTER_OF_CREDIT',newValue:'Amount=100'}),/Amount=100/);
});
test('labels support translations without changing stored role values',()=>{
 const html=fixture({'audit.change.before':'Vorher','audit.change.after':'Nachher','audit.change.active':'Aktiv'}).render({entityType:'USER',newValue:JSON.stringify({role:'EDITOR',active:true})});assert.match(html,/Vorher/);assert.match(html,/Nachher/);assert.match(html,/EDITOR/);assert.match(html,/Aktiv/);
});
test('role events have their own filter and formatter loads before the app',()=>{
 const app=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
 const source=app.match(/function auditArea\(event\)\{[^\n]*?return'LC'\}/)[0];const area=vm.runInNewContext('('+source+')');
 assert.equal(area({action:'ROLE_UPDATED'}),'ROLE');assert.equal(area({entityType:'ROLE'}),'ROLE');assert.equal(area({action:'USER_UPDATED'}),'USER');
 const html=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/index.html'),'utf8');assert.match(html,/<option value="ROLE"/);assert.ok(html.indexOf('/audit-changes.js?')<html.indexOf('/app.js?'));
});
