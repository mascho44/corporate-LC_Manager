/* Presentation only: never changes authorization or hides modules. */
function workspaceProfile(settings){
 if(typeof settings?.bankEnabled!=='boolean'||typeof settings?.corporateEnabled!=='boolean')return 'general';
 const bank=settings?.bankEnabled===true,corporate=settings?.corporateEnabled===true;
 return bank&&corporate?'combined':bank?'bank':corporate?'corporate':'general';
}
function applyWorkspaceProfile(settings){
 const mode=workspaceProfile(settings),hero=document.querySelector('#cockpitSection'),nav=document.querySelector('#appNav nav');if(!hero||!nav)return;
 const text=(key,fallback)=>{const value=globalThis.LcI18n?.t(key);return value&&value!==key?value:fallback;};
 const node=(tag,key,fallback)=>{const n=document.createElement(tag);n.dataset.i18n=key;n.textContent=text(key,fallback);return n;};
 const titles={bank:'Bank workspace',corporate:'Corporate workspace',combined:'Bank & Corporate workspace',general:'Letters of credit at a glance'};
 const descriptions={bank:'Review LC files and documents, track discrepancies and follow up on deadlines.',corporate:'Prepare LC documents, manage company templates and track presentation deadlines.',combined:'Prepare documents and review LC files in one shared workspace.',general:'Manage LC files, documents and deadlines.'};
 const title=hero.querySelector('h1'),description=hero.querySelector('p:not(.eyebrow)');
 if(title){title.dataset.i18n='workspace.'+mode+'.title';title.textContent=text(title.dataset.i18n,titles[mode]);}
 if(description){description.dataset.i18n='workspace.'+mode+'.description';description.textContent=text(description.dataset.i18n,descriptions[mode]);}
 const cockpit=nav.querySelector('[data-app-section="cockpit"]');if(cockpit){cockpit.dataset.profileI18n='workspace.'+mode+'.nav';cockpit.dataset.i18n=cockpit.dataset.profileI18n;cockpit.textContent=text(cockpit.dataset.i18n,titles[mode]);}
 // Move existing business buttons only: handlers, hidden states and admin grouping survive.
 const selectors=mode==='corporate'?['[data-app-section="cockpit"]','[data-app-section="lcs"]','#appNavInbox','#appNavMyWork','#appNavTraining','#appNavImport','[data-app-section="imports"]']:['[data-app-section="cockpit"]','#appNavMyWork','[data-app-section="lcs"]','#appNavInbox','#appNavTraining','#appNavImport','[data-app-section="imports"]'];
 const boundary=nav.querySelector('.admin-nav-label');if(boundary)selectors.forEach(selector=>{const button=nav.querySelector(selector);if(button)nav.insertBefore(button,boundary);});
 let shortcuts=document.querySelector('#workspaceProfileShortcuts');if(!shortcuts){shortcuts=document.createElement('section');shortcuts.id='workspaceProfileShortcuts';hero.append(shortcuts);}shortcuts.replaceChildren();
 shortcuts.append(node('h2','workspace.shortcuts','Quick access'));
 const actions={files:['[data-app-section="lcs"]','nav.lcs','LC files'],work:['#appNavMyWork','nav.myWork','My work'],inbox:['#appNavInbox','nav.inbox','Document inbox','DOCUMENT_UPLOAD'],templates:['#appNavTemplates','nav.templates','Document templates','SETTINGS_MANAGE']};
 const choices=mode==='corporate'?['files','templates','inbox']:mode==='combined'?['files','work','templates','inbox']:['work','files','inbox'];
 choices.forEach(key=>{const [selector,label,fallback,permission]=actions[key];if(permission&&(typeof can!=='function'||!can(permission)))return;const target=nav.querySelector(selector);if(!target)return;const button=node('button',label,fallback);button.type='button';button.className='secondary';button.onclick=()=>target.click();shortcuts.append(button);});
 shortcuts.append(node('p','workspace.presentationOnly','This profile changes navigation priorities only. Available functions and role permissions remain unchanged.'));
}
