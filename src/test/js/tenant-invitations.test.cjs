const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
function load(){const context=vm.createContext({});context.globalThis=context;vm.runInContext(fs.readFileSync(path.join(root,'tenant-invitations.js'),'utf8'),context);return context.LcTenantInvitations;}
test('delivery status is shown in words and unknown values stay visible',()=>{
 const {statusText}=load();
 assert.equal(statusText('SENT'),'versendet');assert.equal(statusText('smtp_failed'),'Versand fehlgeschlagen');assert.equal(statusText('PENDING_MAIL'),'wird versendet');assert.equal(statusText('MAIL_RETRY'),'Versand wird erneut versucht');assert.equal(statusText('EXPIRED'),'abgelaufen');assert.equal(statusText('SOMETHING'),'SOMETHING');assert.equal(statusText(null),'');
});
test('the module is loaded, hooked into the membership assignment and builds no HTML from data',()=>{
 const html=fs.readFileSync(path.join(root,'index.html'),'utf8'),app=fs.readFileSync(path.join(root,'app.js'),'utf8'),code=fs.readFileSync(path.join(root,'tenant-invitations.js'),'utf8');
 assert.ok(html.includes('/tenant-invitations.js'));assert.ok(app.includes('LcTenantInvitations?.render'));assert.equal(code.includes('innerHTML'),false);
});
