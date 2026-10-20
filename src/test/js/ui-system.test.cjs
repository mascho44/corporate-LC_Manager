const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static/');
const read=f=>fs.readFileSync(path.join(root,f),'utf8');
test('the shared control sizes are defined once: 32px standard, 28px small, one radius and two type sizes',()=>{
 const css=read('ui-system.css');
 for(const token of ['--control-height:32px','--control-height-sm:28px','--control-radius:8px','--control-font:14px','--control-font-sm:13px'])assert.ok(css.includes(token),token);
 assert.ok(read('controls.css').includes('--control-height:32px'),'controls.css must not keep an older height');
});
test('every page that loads the base controls loads the control system last',()=>{
 for(const page of ['index.html','platform.html']){
  const html=read(page),links=[...html.matchAll(/<link rel="stylesheet" href="([^"?]+)/g)].map(m=>m[1]);
  assert.equal(links[links.length-1],'/ui-system.css',page+' must load ui-system.css after all other styles');
 }
});
test('navigation, tabs, icon buttons and thumbnails stay out of the control system',()=>{
 const css=read('ui-system.css');
 for(const excluded of ['.ghost','.pu-tab','.app-nav button','.dossier-nav button','.snippet-zoom'])assert.ok(css.includes(':not('+excluded+')'),excluded);
});
test('the tenant page styles no longer size controls themselves',()=>{
 const css=read('tenant-compact.css').replace(/\/\*[\s\S]*?\*\//g,'').replace(/[^{}]*\[type=checkbox\][^{}]*\{[^}]*\}/g,''); // the checkbox reset is not a size
 assert.equal(/#tenantSection\s+(button|select|input)[^{]*\{[^}]*(min-height|font-size|border-radius|padding)/.test(css),false);
});
