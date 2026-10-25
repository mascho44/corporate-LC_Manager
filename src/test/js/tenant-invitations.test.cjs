const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
function load(){const context=vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),});context.globalThis=context;vm.runInContext(fs.readFileSync(path.join(root,'tenant-invitations.js'),'utf8'),context);return context.LcTenantInvitations;}
test('delivery status is shown in words and unknown values stay visible',()=>{
 const {statusText}=load();
 assert.equal(statusText('SENT'),'versendet');assert.equal(statusText('smtp_failed'),'Versand fehlgeschlagen');assert.equal(statusText('PENDING_MAIL'),'wird versendet');assert.equal(statusText('MAIL_RETRY'),'Versand wird erneut versucht');assert.equal(statusText('EXPIRED'),'abgelaufen');assert.equal(statusText('SOMETHING'),'SOMETHING');assert.equal(statusText(null),'');
});
test('the module is loaded, hooked into the membership assignment and builds no HTML from data',()=>{
 const html=fs.readFileSync(path.join(root,'index.html'),'utf8'),app=require('./app-source.cjs')(),code=fs.readFileSync(path.join(root,'tenant-invitations.js'),'utf8');
 assert.ok(html.includes('/tenant-invitations.js'));assert.ok(app.includes('LcTenantInvitations?.render'));assert.equal(code.includes('innerHTML'),false);
});
test('cancelled mail and permission codes are shown in words',()=>{
 const {statusText,permissionText}=load();
 assert.equal(statusText('MAIL_CANCELLED'),'Versand abgebrochen');assert.equal(statusText('MAIL_FAILED'),'Versand fehlgeschlagen');
 assert.equal(permissionText(['USER_MANAGE','AUDIT_VIEW','NEW_CODE']),'Audit-Protokoll ansehen · Benutzer verwalten · NEW_CODE');
 assert.equal(permissionText([]),'keine');assert.equal(permissionText(null),'keine');
});
test('pasted people are parsed from several notations, duplicates and bad lines are reported', () => {
 const vm2 = require('node:vm'), fs2 = require('node:fs');
 const ctx = vm2.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),}); ctx.globalThis = ctx; vm2.runInContext(fs2.readFileSync('src/main/resources/static/tenant-invitations.js', 'utf8'), ctx);
 const api = ctx.LcTenantInvitations;
 const result = api.parseInviteLines('Anna Beispiel <anna@firma.de>\nben.muster@firma.de\ncem@firma.de; Cem Muster\nDora Test, dora@firma.de\n\nkein mail\nANNA@firma.de\n"Eva Neu" <eva@firma.de>');
 assert.deepEqual(Array.from(result.entries, e => e.displayName + '|' + e.email), ['Anna Beispiel|anna@firma.de', 'Ben Muster|ben.muster@firma.de', 'Cem Muster|cem@firma.de', 'Dora Test|dora@firma.de', 'Eva Neu|eva@firma.de']);
 assert.equal(result.problems.length, 2); assert.match(result.problems[0], /Zeile 6/); assert.match(result.problems[1], /Zeile 7.*schon in der Liste/);
 assert.equal(api.nameFromEmail('max_mustermann@x.de'), 'Max Mustermann');
 const many = api.parseInviteLines(Array.from({ length: 60 }, (_, i) => `p${i}@firma.de`).join('\n'));
 assert.equal(many.entries.length, 50); assert.match(many.problems[0], /ersten 50/);
});
test('the role summary names the missing rights when there are few, only counts when many', () => {
 const ctx = require('node:vm').createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),}); ctx.globalThis = ctx; require('node:vm').runInContext(require('node:fs').readFileSync('src/main/resources/static/tenant-invitations.js', 'utf8'), ctx);
 const api = ctx.LcTenantInvitations;
 const all = ['AUDIT_VIEW', 'DOCUMENT_APPROVE', 'DOCUMENT_DELETE', 'DOCUMENT_GENERATE', 'DOCUMENT_REVIEW', 'DOCUMENT_UPLOAD', 'EMAIL_SEND', 'LC_DELETE', 'LC_EDIT', 'SETTINGS_MANAGE', 'SWIFT_IMPORT', 'TRAINING_MANAGE', 'USER_MANAGE'];
 assert.equal(api.permissionSummary(all), 'Alle 13 Rechte'); assert.equal(api.permissionSummary([]), 'Keine Rechte (nur ansehen)');
 assert.equal(api.permissionSummary(all.filter(c => c !== 'USER_MANAGE' && c !== 'LC_DELETE')), '11 von 13 Rechten – es fehlen: Benutzer verwalten, LC-Akten löschen');
 assert.equal(api.permissionSummary(['LC_EDIT', 'DOCUMENT_UPLOAD']), '2 von 13 Rechten');
});
