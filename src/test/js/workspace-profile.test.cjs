const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
function fixture(permissions=[]){
 const nodes=new Map(),clicks=[],moves=[];
 function node(tag){let id='';const n={tag,dataset:{},children:[],textContent:'',append(...items){this.children.push(...items);},replaceChildren(...items){this.children=items;}};Object.defineProperty(n,'id',{get:()=>id,set:v=>{id=v;nodes.set('#'+v,n);}});return n;}
 const hero=node('section'),title=node('h1'),description=node('p');hero.querySelector=s=>s==='h1'?title:description;nodes.set('#cockpitSection',hero);
 const selectors=['[data-app-section="cockpit"]','[data-app-section="lcs"]','#appNavInbox','#appNavMyWork','#appNavTraining','#appNavImport','[data-app-section="imports"]','#appNavTemplates','.admin-nav-label'];
 const menu=new Map(selectors.map(s=>{const n=node('button');n.selector=s;n.className=s==='#appNavInbox'?'hidden':'';n.onclick=()=>clicks.push(s);n.click=()=>n.onclick();return[s,n];}));
 const nav={querySelector:s=>menu.get(s),insertBefore:n=>moves.push(n.selector)};nodes.set('#appNav nav',nav);
 const context=vm.createContext({document:{querySelector:s=>nodes.get(s),createElement:node},can:p=>permissions.includes(p)});
 vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/workspace-profile.js'),'utf8'),context);
 return{context,nodes,hero,title,description,menu,moves,clicks};
}
test('profile classification uses strict booleans and a general fallback',()=>{
 const {context:c}=fixture();assert.equal(c.workspaceProfile({bankEnabled:true,corporateEnabled:false}),'bank');assert.equal(c.workspaceProfile({bankEnabled:false,corporateEnabled:true}),'corporate');assert.equal(c.workspaceProfile({bankEnabled:true,corporateEnabled:true}),'combined');assert.equal(c.workspaceProfile({bankEnabled:'true',corporateEnabled:null}),'general');assert.equal(c.workspaceProfile(undefined),'general');
});
test('Corporate prioritizes files and templates without replacing menu handlers or hidden states',()=>{
 const f=fixture(['SETTINGS_MANAGE']);const handler=f.menu.get('#appNavInbox').onclick;f.context.applyWorkspaceProfile({corporateEnabled:true,bankEnabled:false});assert.equal(f.title.textContent,'Corporate workspace');assert.equal(f.moves[1],'[data-app-section="lcs"]');assert.equal(f.menu.get('#appNavInbox').className,'hidden');assert.equal(f.menu.get('#appNavInbox').onclick,handler);
 const buttons=f.nodes.get('#workspaceProfileShortcuts').children.filter(n=>n.tag==='button');assert.equal(buttons.length,2);buttons[1].onclick();assert.equal(f.clicks[0],'#appNavTemplates');
});
test('Bank and combined quick links respect existing permissions and refresh without duplicates',()=>{
 const f=fixture(['DOCUMENT_UPLOAD','SETTINGS_MANAGE']);f.context.applyWorkspaceProfile({bankEnabled:true,corporateEnabled:false});assert.equal(f.title.textContent,'Bank workspace');assert.equal(f.moves[1],'#appNavMyWork');const panel=f.nodes.get('#workspaceProfileShortcuts');assert.equal(panel.children.filter(n=>n.tag==='button').length,3);
 f.context.applyWorkspaceProfile({bankEnabled:true,corporateEnabled:true});assert.equal(f.title.textContent,'Bank & Corporate workspace');assert.equal(panel.children.filter(n=>n.tag==='button').length,4);assert.equal(f.hero.children.length,1);
});
test('read-only quick links do not expose administrative actions and labels are translatable text',()=>{
 const f=fixture();f.context.LcI18n={t:key=>key==='workspace.bank.title'?'Bank-Arbeitsbereich':key};f.context.applyWorkspaceProfile({bankEnabled:true,corporateEnabled:false});assert.equal(f.title.textContent,'Bank-Arbeitsbereich');assert.equal(f.title.dataset.i18n,'workspace.bank.title');assert.equal(f.title.innerHTML,undefined);assert.equal(f.nodes.get('#workspaceProfileShortcuts').children.filter(n=>n.tag==='button').length,2);assert.match(f.nodes.get('#workspaceProfileShortcuts').children.at(-1).textContent,/permissions remain unchanged/);
});
test('profile script loads before tenant refresh and retains i18n profile navigation keys',()=>{
 const root=path.resolve(__dirname,'../../main/resources/static'),html=fs.readFileSync(path.join(root,'index.html'),'utf8');assert.ok(html.indexOf('/workspace-profile.js')<html.indexOf('/tenants.js'));assert.match(fs.readFileSync(path.join(root,'i18n.js'),'utf8'),/dataset.profileI18n\|\|key/);
});
