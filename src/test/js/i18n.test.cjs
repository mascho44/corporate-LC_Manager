const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../../main/resources/static');
function fixture(overrides={}){
 const calls=[],document={documentElement:{lang:'de'},querySelectorAll:()=>[],addEventListener(){},dispatchEvent(){}};
 const context=vm.createContext({document,Intl,Date,Event,MutationObserver:class{},fetch:async url=>{calls.push(url);if(overrides.failGerman&&url==='/language-de.json')throw Error('offline');const data=JSON.parse(fs.readFileSync(path.join(root,url.slice(1))));if(overrides.missingKey&&url==='/language-de.json')delete data['nav.lcs'];return{ok:true,json:async()=>data};}});
 vm.runInContext(fs.readFileSync(path.join(root,'i18n.js'),'utf8'),context);return{api:context.LcI18n,document,calls};
}
test('English is the default and unknown packs cannot select arbitrary URLs',async()=>{const f=fixture();await f.api.setLanguage('../../evil');assert.equal(f.api.language(),'en');assert.equal(f.api.t('nav.lcs'),'LC files');assert.deepEqual(f.calls,['/language-en.json']);assert.equal(f.document.documentElement.lang,'en');});
test('German missing keys fall back to English',async()=>{const f=fixture({missingKey:true});await f.api.setLanguage('de');assert.equal(f.api.t('nav.logout'),'Abmelden');assert.equal(f.api.t('nav.lcs'),'LC files');assert.equal(f.api.locale(),'de-DE');});
test('optional pack failure keeps readable English',async()=>{const f=fixture({failGerman:true});await f.api.setLanguage('de');assert.equal(f.api.t('nav.lcs'),'LC files');});
test('formatting follows the active locale without changing stored values',async()=>{const f=fixture();await f.api.setLanguage('en');assert.equal(f.api.number(1234.5),'1,234.5');await f.api.setLanguage('de');assert.equal(f.api.number(1234.5),'1.234,5');});
test('all first-party German keys have an English baseline',()=>{const en=JSON.parse(fs.readFileSync(path.join(root,'language-en.json'))),de=JSON.parse(fs.readFileSync(path.join(root,'language-de.json')));assert.deepEqual(Object.keys(en).sort(),Object.keys(de).sort());});
