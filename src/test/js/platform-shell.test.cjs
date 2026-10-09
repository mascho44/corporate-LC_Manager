const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/platform-shell.js'),'utf8');
test('platform client rejects tenant APIs and sends the CSRF token for global writes',async()=>{
 const calls=[],context=vm.createContext({document:{addEventListener(){}},fetch:async(url,options)=>{calls.push({url,options});return {ok:true,status:204};},location:{replace:assert.fail}});
 vm.runInContext(source,context);vm.runInContext("csrfToken='synthetic-token'",context);
 await assert.rejects(context.json('/api/lcs'),/Only global platform APIs/);assert.equal(calls.length,0);
 await context.json('/api/platform/users/id/access',{method:'PUT',headers:{'Content-Type':'application/json'},body:'{}'});
 assert.equal(calls[0].options.headers['X-CSRF-TOKEN'],'synthetic-token');assert.equal(calls[0].options.headers['Content-Type'],'application/json');
});
test('platform page has no workspace selector or business application scripts',()=>{
 const html=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/platform.html'),'utf8');
 assert.match(html,/GLOBAL SCOPE/);assert.match(html,/platform-shell.js/);assert.doesNotMatch(html,/src="\/app.js|src="\/tenants.js|id="tenantChooser"|lcSection|cockpitSection/);
});
