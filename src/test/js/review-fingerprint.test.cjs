const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');

test('review submission sends the displayed finding fingerprint',async()=>{
  const start=source.indexOf('async function decideCheck(');
  const end=source.indexOf('function downloadCheckReport()',start);
  let submitted;let refreshed=false;
  const context=vm.createContext({activeLc:{id:'lc-test'},prompt:()=> 'Original geprüft',alert:assert.fail,
    json:async(url,request)=>{submitted=JSON.parse(request.body);},show:async()=>{refreshed=true;},activateDossierSection:()=>{}});
  vm.runInContext(source.slice(start,end),context);
  await context.decideCheck('INVOICE_AMOUNT_EXCEEDED','invoice.pdf','ACCEPTED','a'.repeat(64));
  assert.equal(submitted.reviewFingerprint,'a'.repeat(64));
  assert.equal(refreshed,true);
  assert.match(source,/decision\.dataset\.checkFingerprint/);
});
test('review actions carry fingerprints, respect rights and do not confirm stale notices',()=>{
  const start=source.indexOf('function renderReview('),end=source.indexOf('\n',start);
  const context=vm.createContext({can:()=>true,esc:value=>String(value||''),reviewGate:()=>'',findingShortcutHelp:()=>'',label:value=>value,renderFindingEvidence:()=>'',renderRuleMetadata:()=>''});
  vm.runInContext(source.slice(start,end),context);
  const finding={severity:'WARNING',code:'DATE_NOT_CAPTURED',message:'Datum prüfen',reviewFingerprint:'a'.repeat(64)};
  const review={discrepancies:0,warnings:2,passed:0,results:[finding,{...finding,code:'REVIEW_STALE'}]};
  const html=context.renderReview(review);
  assert.equal(html.split('data-check-fingerprint=').length-1,2);
  assert.match(html,/data-review-history/);
  assert.doesNotMatch(html,/data-check-code="REVIEW_STALE"/);
  context.can=right=>right==='AUDIT_VIEW';
  assert.doesNotMatch(context.renderReview(review),/data-check-decision=/);
});
