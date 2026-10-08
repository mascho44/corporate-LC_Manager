const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
test('tenant archival is rendered as an explicit translated change',()=>{const html=fixture({'audit.change.archived':'Archiviert'}).render({entityType:'TENANT',previousValue:'{"active":false,"archived":false}',newValue:'{"active":false,"archived":true}'});assert.match(html,/Archiviert/);assert.equal((html.match(/class="audit-field-changed"/g)||[]).length,1);});
function fixture(translations){const context=vm.createContext({LcI18n:translations?{t:key=>translations[key]||key}:undefined});vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/audit-changes.js'),'utf8'),context);return context.LcAuditChanges;}
test('membership audit shows role and permissions without identity credentials',()=>{
 const html=fixture().render({entityType:'MEMBERSHIP',previousValue:JSON.stringify({username:'Synthetic',roleId:'old',permissions:['LC_EDIT'],active:true}),newValue:JSON.stringify({username:'Synthetic',roleId:'new',permissions:['DOCUMENT_REVIEW'],active:true,passwordHash:'secret-not-rendered'})});
 assert.match(html,/<table/);assert.match(html,/DOCUMENT_REVIEW/);assert.match(html,/old/);assert.match(html,/new/);assert.doesNotMatch(html,/secret-not-rendered/);
});
test('membership access audit displays suspension independently of global activation',()=>{
 const html=fixture().render({entityType:'MEMBERSHIP',previousValue:JSON.stringify({active:true,suspended:false}),newValue:JSON.stringify({active:false,suspended:true})});
 assert.match(html,/Membership suspended/);assert.equal((html.match(/class="audit-field-changed"/g)||[]).length,2);
});
test('administration snapshots display before/after fields and highlight changed values',()=>{
 const api=fixture(),html=api.render({entityType:'USER',previousValue:JSON.stringify({username:'Synthetic',role:'EDITOR',active:true}),newValue:JSON.stringify({username:'Synthetic',role:'VIEWER',active:false})});
 assert.match(html,/<table/);assert.match(html,/Before/);assert.match(html,/After/);assert.match(html,/EDITOR/);assert.match(html,/VIEWER/);assert.equal((html.match(/class="audit-field-changed"/g)||[]).length,2);
});
test('untrusted names and permission strings cannot inject markup',()=>{
 const html=fixture().render({entityType:'ROLE',newValue:JSON.stringify({name:'<IMG src=x onerror=alert(1)>',permissions:['<SCRIPT>bad</SCRIPT>'],passwordHash:'should-not-render'})});
 assert.doesNotMatch(html,/<img|<script>|should-not-render/i);assert.match(html,/&lt;img/i);assert.match(html,/&lt;script/i);
});
test('creation/deletion and malformed or legacy snapshots remain readable',()=>{
 const api=fixture();assert.equal(api.render({}), '');assert.match(api.render({entityType:'USER',previousValue:JSON.stringify({username:'Synthetic'}),newValue:null}),/Synthetic/);
 const html=api.render({entityType:'ROLE',previousValue:'not JSON <SCRIPT>',newValue:'{"name":'});assert.doesNotMatch(html,/<table|<script>/i);assert.match(html,/not JSON &lt;script&gt;/i);
 assert.match(api.render({entityType:'LETTER_OF_CREDIT',newValue:'Amount=100'}),/Amount=100/);
});
test('labels support translations without changing stored role values',()=>{
 const html=fixture({'audit.change.before':'Vorher','audit.change.after':'Nachher','audit.change.active':'Aktiv'}).render({entityType:'USER',newValue:JSON.stringify({role:'EDITOR',active:true})});assert.match(html,/Vorher/);assert.match(html,/Nachher/);assert.match(html,/EDITOR/);assert.match(html,/Aktiv/);
});
test('role events have their own filter and formatter loads before the app',()=>{
 const app=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
 const source=app.match(/function auditArea\(event\)\{[^\n]*?return'LC'\}/)[0];const area=vm.runInNewContext('('+source+')');
 assert.equal(area({action:'ROLE_UPDATED'}),'ROLE');assert.equal(area({entityType:'ROLE'}),'ROLE');assert.equal(area({action:'USER_UPDATED'}),'USER');
 assert.equal(area({action:'TENANT_CREATED'}),'TENANT');assert.equal(area({action:'TENANT_SELECTED'}),'TENANT');assert.equal(area({entityType:'TENANT'}),'TENANT');assert.equal(area({action:'LOGIN',entityType:'SESSION'}),'SESSION');
 const html=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/index.html'),'utf8');assert.match(html,/<option value="ROLE"/);assert.ok(html.indexOf('/audit-changes.js?')<html.indexOf('/app.js?'));
});
test('tenant settings show allowlisted name and language changes without role labels or credentials',()=>{
 const html=fixture().render({entityType:'TENANT',previousValue:JSON.stringify({name:'Synthetic old',defaultLanguage:'en'}),newValue:JSON.stringify({name:'<script>synthetic</script>',defaultLanguage:'de',passwordHash:'secret-not-rendered',token:'hidden-token'})});
 assert.match(html,/<table/);assert.match(html,/Tenant name/);assert.match(html,/Default language/);assert.doesNotMatch(html,/Role name|secret-not-rendered|hidden-token|<script>/);assert.match(html,/&lt;script&gt;/);assert.equal((html.match(/class="audit-field-changed"/g)||[]).length,2);
 const translated=fixture({'audit.change.tenantName':'Mandantenname','audit.change.defaultLanguage':'Standardsprache'}).render({entityType:'TENANT',newValue:JSON.stringify({name:'Synthetic',defaultLanguage:'en'})});assert.match(translated,/Mandantenname/);assert.match(translated,/Standardsprache/);
 const page=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/index.html'),'utf8');assert.match(page,/<option value="TENANT"/);
});
