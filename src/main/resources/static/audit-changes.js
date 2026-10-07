(() => {
 const escape=value=>String(value??'–').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const text=(key,fallback)=>{const translated=globalThis.LcI18n?.t('audit.change.'+key);return translated&&translated!=='audit.change.'+key?translated:fallback;};
 const label=(key,fallback)=>`<span data-i18n="audit.change.${key}">${escape(text(key,fallback))}</span>`;
 function parse(value){if(!value)return null;try{const result=JSON.parse(value);return result&&typeof result==='object'&&!Array.isArray(result)?result:null;}catch{return null;}}
 const definitions={name:'Role name',baseRole:'Base role',systemRole:'System role',permissions:'Permissions',username:'Username',roleId:'Assigned role ID',role:'Base role',active:'Active',suspended:'Membership suspended',platformAdministrator:'Platform administrator',tenantId:'Tenant ID',status:'Invitation status'};
 function value(item){if(item===undefined||item===null)return'–';if(typeof item==='boolean')return label(item?'yes':'no',item?'Yes':'No');if(Array.isArray(item))return item.map(escape).join(' · ')||'–';return escape(item);}
 function render(event){
  if(!event.previousValue&&!event.newValue)return'';
  const before=parse(event.previousValue),after=parse(event.newValue);
  const tenant=event.entityType==='TENANT';
  const keys=tenant?['name','defaultLanguage']:event.entityType==='ROLE'?['name','baseRole','systemRole','permissions']:event.entityType==='USER'?['username','roleId','role','active','platformAdministrator']:event.entityType==='INVITATION'?['tenantId','roleId','status']:event.entityType==='MEMBERSHIP'?['username','roleId','permissions','active','suspended']:[];
  const structured=keys.length&&(!event.previousValue||before)&&(!event.newValue||after);
  const rows=structured?keys.filter(key=>Object.hasOwn(before||{},key)||Object.hasOwn(after||{},key)).map(key=>{
   const old=before?.[key],next=after?.[key];const changed=JSON.stringify(old)!==JSON.stringify(next);
   const fieldLabel=tenant?label(key==='name'?'tenantName':'defaultLanguage',key==='name'?'Tenant name':'Default language'):label(key,definitions[key]);
   return `<tr class="${changed?'audit-field-changed':''}"><th scope="row">${fieldLabel}${changed?' '+label('changed','Changed'):''}</th><td>${value(old)}</td><td>${value(next)}</td></tr>`;
  }).join(''):'';
  const content=rows?`<div class="audit-change-scroll"><table class="audit-fields"><thead><tr><th>${label('field','Field')}</th><th>${label('before','Before')}</th><th>${label('after','After')}</th></tr></thead><tbody>${rows}</tbody></table></div>`:`<small><strong>${label('before','Before')}</strong>${escape(event.previousValue)}</small><small><strong>${label('after','After')}</strong>${escape(event.newValue)}</small>`;
  return `<details class="audit-change"><summary>${label('show','Show changes')}</summary>${content}</details>`;
 }
 globalThis.LcAuditChanges={render};
})();
