/* Read-only platform audit metadata and chain verification. No business data, audit details or values are shown. */
async function setupPlatformOverview(section){
 const t=(key,fallback)=>{const value=globalThis.LcI18n?.t(key);return value&&value!==key?value:fallback;};
 const node=(tag,value,className)=>{const n=document.createElement(tag);n.textContent=value;if(className)n.className=className;return n;};
 const auditBox=document.createElement('section');
 const tenantFilter=document.createElement('input');tenantFilter.placeholder=t('platformOverview.auditTenant','Tenant code (optional)');tenantFilter.maxLength=100;tenantFilter.setAttribute('aria-label',tenantFilter.placeholder);
 const auditReload=node('button',t('platformOverview.auditReload','Load audit'),'secondary');auditReload.type='button';
 const auditStatus=node('p','');auditStatus.setAttribute('role','status');const auditList=document.createElement('div');
 const chainButton=node('button',t('platformOverview.verifyChains','Verify audit chains'),'secondary');chainButton.type='button';const chainOutput=document.createElement('div');chainOutput.setAttribute('role','status');
 auditBox.append(node('h3',t('platformOverview.audit','Platform audit (metadata only)')),node('p',t('platformOverview.auditNote','Time, tenant, user and action only. Details and values stay in the tenant audit log.')),tenantFilter,auditReload,auditStatus,auditList,chainButton,chainOutput);
 section.append(auditBox);
 async function refreshAudit(){
  auditStatus.textContent=t('platformOverview.loading','Loading…');auditList.replaceChildren();
  const query=new URLSearchParams({limit:'200'});if(tenantFilter.value.trim())query.set('tenant',tenantFilter.value.trim());
  try{const events=await json('/api/platform/audit?'+query);
   events.forEach(e=>{const row=document.createElement('article');row.className='membership-row';
    row.append(node('small',new Date(e.occurredAt).toLocaleString()),node('b',e.action),node('span',e.tenantCode+' · '+e.username+(e.entityType?' · '+e.entityType:'')),node('span',e.successful?t('platformOverview.ok','OK'):t('platformOverview.failed','Failed'),'import-status '+(e.successful?'success':'rejected')));auditList.append(row);});
   auditStatus.textContent=events.length+' '+t('platformOverview.events','events');
  }catch(failure){auditStatus.textContent=failure.message;}
 }

 chainButton.onclick=async()=>{
  chainButton.disabled=true;chainOutput.replaceChildren(node('p',t('platformOverview.loading','Loading…')));
  try{const chains=await json('/api/platform/audit/chain');chainOutput.replaceChildren();
   chains.forEach(c=>{const row=document.createElement('article');row.className='membership-row';
    row.append(node('b',c.tenantCode),node('span',c.ok?t('platformOverview.chainOk','Chain intact'):t('platformOverview.chainBroken','Chain broken'),'import-status '+(c.ok?'success':'rejected')),
     node('small',c.ok?c.checked+' '+t('platformOverview.chainChecked','entries checked')+(c.unchained?' · '+c.unchained+' '+t('platformOverview.chainUnchained','older entries not chained'):'')+(c.headHash?' · #'+c.headSeq+' '+c.headHash:''):t('platformOverview.chainBreakAt','Break at no.')+' '+c.firstBadSeq+' · '+(c.reason||'')));
    chainOutput.append(row);});}
  catch(failure){chainOutput.replaceChildren(node('p',failure.message));}finally{chainButton.disabled=false;}
 };
 auditReload.onclick=refreshAudit;
 return refreshAudit;
}
