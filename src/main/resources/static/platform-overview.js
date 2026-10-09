/* Read-only cross-tenant overview: memberships and audit metadata. No business data, audit details or values are shown. */
async function setupPlatformOverview(section){
 const t=(key,fallback)=>{const value=globalThis.LcI18n?.t(key);return value&&value!==key?value:fallback;};
 const node=(tag,value,className)=>{const n=document.createElement(tag);n.textContent=value;if(className)n.className=className;return n;};
 const membershipsBox=document.createElement('section'),auditBox=document.createElement('section');
 const membershipStatus=node('p','');membershipStatus.setAttribute('role','status');const membershipList=document.createElement('div');
 const filter=document.createElement('input');filter.type='search';filter.placeholder=t('platformOverview.filter','Filter by user or tenant');filter.maxLength=100;filter.setAttribute('aria-label',filter.placeholder);
 membershipsBox.append(node('h3',t('platformOverview.memberships','Memberships across tenants')),node('p',t('platformOverview.membershipNote','Role and access of identities from other tenants can be changed here. Home identities and the bootstrap tenant are managed in their own tenant; the last active administrator of a tenant is always protected.')),filter,membershipStatus,membershipList);
 const tenantFilter=document.createElement('input');tenantFilter.placeholder=t('platformOverview.auditTenant','Tenant code (optional)');tenantFilter.maxLength=100;tenantFilter.setAttribute('aria-label',tenantFilter.placeholder);
 const auditReload=node('button',t('platformOverview.auditReload','Load audit'),'secondary');auditReload.type='button';
 const auditStatus=node('p','');auditStatus.setAttribute('role','status');const auditList=document.createElement('div');
 auditBox.append(node('h3',t('platformOverview.audit','Platform audit (metadata only)')),node('p',t('platformOverview.auditNote','Time, tenant, user and action only. Details and values stay in the tenant audit log.')),tenantFilter,auditReload,auditStatus,auditList);
 section.append(membershipsBox,auditBox);
 let rows=[],choices=[],generation=0;
 const yesNo=value=>value?t('platformOverview.yes','yes'):t('platformOverview.no','no');
 function renderMemberships(){
  const q=filter.value.trim().toLowerCase();membershipList.replaceChildren();
  const shown=rows.filter(r=>!q||[r.username,r.displayName,r.tenantCode,r.tenantName,r.roleName].some(v=>String(v||'').toLowerCase().includes(q)));
  shown.forEach(r=>{const row=document.createElement('article');row.className='membership-row';
   const who=document.createElement('div');who.append(node('b',r.displayName||r.username),node('small',r.username));
   const where=document.createElement('div');where.append(node('b',r.tenantName),node('small',r.tenantCode+' · '+r.roleName));
   const state=node('span',(!r.accountActive?t('platformOverview.accountSuspended','Account suspended'):r.suspended||!r.membershipActive?t('platformOverview.membershipSuspended','Membership suspended'):!r.tenantActive?t('platformOverview.tenantSuspended','Tenant suspended'):t('platformOverview.active','Active')),'import-status '+(r.accountActive&&r.membershipActive&&!r.suspended&&r.tenantActive?'success':'rejected'));
   row.append(who,where,state);
   if(r.manageable){
    const roleSelect=document.createElement('select');roleSelect.setAttribute('aria-label',t('platformOverview.role','Role'));
    const roles=choices.find(c=>c.id===r.tenantId)?.roles||[];
    roles.forEach(role=>{const o=document.createElement('option');o.value=role.id;o.textContent=role.name;roleSelect.append(o);});roleSelect.value=r.roleId;
    const change=node('button',t('platformOverview.changeRole','Change role'),'secondary');change.type='button';change.disabled=roles.length===0;
    const toggle=node('button',r.suspended?t('platformOverview.restoreAccess','Restore access'):t('platformOverview.suspendAccess','Suspend access'),r.suspended?'secondary':'danger');toggle.type='button';
    const message=node('small','');message.setAttribute('role','alert');
    const run=async(button,question,url,body)=>{
     if(button.disabled)return;button.disabled=true;message.textContent='';
     try{if(!await confirmAction(question+'\n\n'+r.username+' · '+r.tenantName))return;await json(url,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});await refreshMemberships();}
     catch(failure){message.textContent=failure.message;}finally{button.disabled=false;}
    };
    change.onclick=()=>run(change,t('platformOverview.confirmRole','Change the role of this identity in this tenant?'),'/api/platform/memberships/'+encodeURIComponent(r.tenantId)+'/'+encodeURIComponent(r.userId)+'/role',{roleId:roleSelect.value});
    toggle.onclick=()=>run(toggle,r.suspended?t('platformOverview.confirmRestore','Restore access for this identity in this tenant?'):t('platformOverview.confirmSuspend','Suspend access for this identity in this tenant?'),'/api/platform/memberships/'+encodeURIComponent(r.tenantId)+'/'+encodeURIComponent(r.userId)+'/access',{suspended:!r.suspended});
    row.append(roleSelect,change,toggle,message);
   }else row.append(node('small',t('platformOverview.homeNote','Managed in the home tenant')));
   membershipList.append(row);});
  membershipStatus.textContent=shown.length+' / '+rows.length+' '+t('platformOverview.membershipsShown','memberships');
 }
 filter.oninput=renderMemberships;
 async function refreshMemberships(){const request=++generation;membershipStatus.textContent=t('platformOverview.loading','Loading…');try{const [data,available]=await Promise.all([json('/api/platform/memberships'),json('/api/platform/invitations/choices').catch(()=>[])]);if(request!==generation)return;rows=data;choices=available||[];renderMemberships();}catch(failure){if(request===generation)membershipStatus.textContent=failure.message;}}
 async function refreshAudit(){
  auditStatus.textContent=t('platformOverview.loading','Loading…');auditList.replaceChildren();
  const query=new URLSearchParams({limit:'200'});if(tenantFilter.value.trim())query.set('tenant',tenantFilter.value.trim());
  try{const events=await json('/api/platform/audit?'+query);
   events.forEach(e=>{const row=document.createElement('article');row.className='membership-row';
    row.append(node('small',new Date(e.occurredAt).toLocaleString()),node('b',e.action),node('span',e.tenantCode+' · '+e.username+(e.entityType?' · '+e.entityType:'')),node('span',e.successful?t('platformOverview.ok','OK'):t('platformOverview.failed','Failed'),'import-status '+(e.successful?'success':'rejected')));auditList.append(row);});
   auditStatus.textContent=events.length+' '+t('platformOverview.events','events');
  }catch(failure){auditStatus.textContent=failure.message;}
 }
 auditReload.onclick=refreshAudit;
 return async()=>{await refreshMemberships();await refreshAudit();};
}
