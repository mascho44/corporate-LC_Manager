const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
test('the compact tenant page styles load after the base styles and only touch the tenant page',()=>{
 const html=fs.readFileSync(path.join(root,'index.html'),'utf8'),css=fs.readFileSync(path.join(root,'tenant-compact.css'),'utf8');
 assert.ok(html.indexOf('/tenant-compact.css')>html.indexOf('/app-polish.css')&&html.indexOf('/tenant-compact.css')>html.indexOf('/ai.css'));
 css.replace(/\/\*[\s\S]*?\*\//g,'').split('}').map(r=>r.split('{')[0].trim()).filter(Boolean).filter(sel=>!sel.startsWith('@media')).forEach(sel=>assert.ok(sel.split(',').every(part=>/^\s*(#tenantSection|#tenantSettingsForm|#tenantReadiness)/.test(part)),'selector outside the tenant page: '+sel));
});
