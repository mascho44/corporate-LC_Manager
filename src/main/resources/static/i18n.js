'use strict';
globalThis.LcI18n=(()=>{
    // Install packs centrally; never accept dictionary paths or HTML from users.
    const packs={en:{locale:'en-GB',file:'/language-en.json'},de:{locale:'de-DE',file:'/language-de.json'}};
    let language='en',english={},messages={},generation=0;
    const t=(key,values={})=>String(messages[key]??english[key]??key).replace(/\{([A-Za-z0-9_]+)\}/g,(_,name)=>String(values[name]??`{${name}}`));
    function apply(root=document){
        root.querySelectorAll('[data-i18n]').forEach(node=>{
            const value=t(node.dataset.i18n),text=[...node.childNodes].find(child=>child.nodeType===3);
            if(text){if(text.textContent.trim()!==value)text.textContent=value+' ';}
            else if(!node.children.length&&node.textContent!==value)node.textContent=value;
        });
        root.querySelectorAll('[data-i18n-placeholder]').forEach(node=>{const value=t(node.dataset.i18nPlaceholder);if(node.placeholder!==value)node.placeholder=value;});
    }
    async function setLanguage(selected){
        const next=Object.hasOwn(packs,selected)?selected:'en',request=++generation;
        const load=async code=>{const response=await fetch(packs[code].file,{credentials:'same-origin'});if(!response.ok)throw new Error('Language pack unavailable');return response.json();};
        const fallback=await load('en');let dictionary=fallback;
        if(next!=='en')try{dictionary=await load(next);}catch{/* English fallback for missing optional pack. */}
        if(request!==generation)return;
        english=fallback;messages=dictionary;language=next;document.documentElement.lang=language;apply();
        document.dispatchEvent(new Event('lc-language-changed'));
    }
    const locale=()=>packs[language].locale;
    return {t,apply,setLanguage,locale,language:()=>language,date:(value,options={})=>new Intl.DateTimeFormat(locale(),options).format(new Date(value)),number:(value,options={})=>new Intl.NumberFormat(locale(),options).format(value)};
})();
document.addEventListener('DOMContentLoaded',()=>{
    const bindings={'[data-app-section="cockpit"]':'nav.cockpit','[data-app-section="lcs"]':'nav.lcs','#appNavMyWork':'nav.myWork','#appNavInbox':'nav.inbox','#appNavTraining':'nav.training','#appNavImport':'nav.import','[data-app-section="imports"]':'nav.imports','#appNavMonitoring':'nav.monitoring','#appNavCompany':'nav.company','#appNavTemplates':'nav.templates','#appNavUsers':'nav.users','#appNavAudit':'nav.audit','#appNavSecurity':'nav.security','#appNavPassword':'nav.password','#appNavLogout':'nav.logout','.info-menu-link':'nav.info','#avatarOpenProfile':'profile.title','#avatarOpenPassword':'profile.password','#avatarOpenTotp':'profile.totp','#avatarLogout':'nav.logout'};
    const annotate=()=>{for(const [selector,key] of Object.entries(bindings)){const node=document.querySelector(selector);if(node&&node.dataset.i18n!==key)node.dataset.i18n=key;}LcI18n.apply();};
    annotate();new MutationObserver(annotate).observe(document.body,{childList:true,subtree:true});
    LcI18n.setLanguage('en').catch(()=>{});
});
