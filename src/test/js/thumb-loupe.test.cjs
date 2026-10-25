const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/thumb-loupe.js'),'utf8');
function load(){const context=vm.createContext({globalThis:null});context.globalThis=context;vm.runInContext(source,context);return context.LcThumbLoupe;}
const rect={left:100,top:50,width:358,height:480};
test('lens centres the point under the pointer',()=>{
 const g=load().lensGeometry(rect,100+179,50+240,2,240);
 assert.equal(g.backgroundSize,'716px 960px');
 assert.equal(g.backgroundPosition,`${120-179*2}px ${120-240*2}px`);
});
test('pointer outside the image is clamped to its edge',()=>{
 const g=load().lensGeometry(rect,0,9999,2,240);
 assert.equal(g.backgroundPosition,`${120-0}px ${120-480*2}px`);
});
test('loupe is wired without a document in tests and the thumbnail script is loaded by the app page',()=>{
 assert.equal(typeof load().lensGeometry,'function');
 assert.match(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/index.html'),'utf8'),/thumb-loupe\.js/);
});
