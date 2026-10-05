const test=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
function fixture(){
 const calls=[];
 function element(){return {classList:{add(){},remove(){},toggle(){}},querySelectorAll(){return[]},querySelector(){return null},prepend(){},before(){},after(){},replaceChildren(){},reset(){},addEventListener(){}};}
 const elements={};const profile=element();profile.value='ADVISING_LETTER';
 const context=vm.createContext({document:{createElement:element,querySelector(selector){if(selector==='#trainingProfileSelect')return profile;if(selector==='#trainingHistory')return null;return elements[selector]??=element();},querySelectorAll(){return[];}},
  currentUser:{username:'owner',role:'EDITOR'},can:()=>true,advisingBody:element(),finishAdvice:element(),advisingTrainingItem:{id:'test-id',username:'owner'},trainingDialog:{open:true},advisingCaseBusy:false,
  esc:value=>String(value),confirm:()=>true,alert:message=>calls.push(['alert',message]),
  json:async(url,options)=>{calls.push([url,options]);return[];},openTraining(){},renderAdvisingTraining(){},saveAdvisingTraining:async()=>{},setTrainingProfile(){},openTrainingSession(){},loadDashboardDrafts:async()=>{},loadTrainingHistory:async()=>{}});
 vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/training-deletion.js'),'utf8'),context);
 return {context,calls,elements};
}
test('owner/admin permissions match soft-delete authorization',()=>{
 const {context}=fixture();assert.equal(context.mayDeleteTraining({username:'owner'}),true);assert.equal(context.mayDeleteTraining({username:'other'}),false);
 context.currentUser.role='ADMIN';assert.equal(context.mayDeleteTraining({username:'other'}),true);
 context.can=()=>false;assert.equal(context.mayDeleteTraining({username:'owner'}),false);
});
test('delete uses existing template endpoint and clears active advice only on success',async()=>{
 const {context,calls}=fixture();await context.removeTrainingTemplates(['test-id'],'Vorlage');
 const call=calls.find(call=>call[0]==='/api/training/templates');assert.equal(call[1].method,'DELETE');assert.equal(call[1].body,'["test-id"]');assert.equal(context.advisingTrainingItem,null);
});
test('cancel and save-in-progress do not delete anything',async()=>{
 const {context,calls}=fixture();context.confirm=()=>false;await context.removeTrainingTemplates(['test-id'],'Vorlage');assert.equal(calls.length,0);
 context.confirm=()=>true;context.advisingCaseBusy=true;await context.removeTrainingTemplates(['test-id'],'Vorlage');assert.equal(calls.some(call=>call[0]==='/api/training/templates'),false);
});
test('failed delete retains draft and displays error',async()=>{
 const {context,elements}=fixture();context.json=async()=>{throw Error('Keine Berechtigung');};await context.removeTrainingTemplates(['test-id'],'Vorlage');
 assert.equal(context.advisingTrainingItem.id,'test-id');assert.match(elements['#advisingTrainingMessage'].textContent,/Keine Berechtigung/);
});
