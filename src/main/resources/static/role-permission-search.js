function setupRolePermissionSearch(){
 const list=document.querySelector('#permissionList');if(!list)return;
 const text=(key,fallback)=>{const value=globalThis.LcI18n?.t(key);return value&&value!==key?value:fallback;};
 let search=document.querySelector('#rolePermissionSearch'),status=document.querySelector('#rolePermissionSearchStatus');
 if(!search){
  const wrapper=document.createElement('div');wrapper.className='role-permission-search';
  const label=document.createElement('label');label.htmlFor='rolePermissionSearch';label.textContent=text('roles.searchPermissions','Find permissions');
  search=document.createElement('input');search.id='rolePermissionSearch';search.type='search';search.maxLength=100;search.setAttribute('aria-controls','permissionList');search.setAttribute('aria-describedby','rolePermissionSearchStatus');
  status=document.createElement('small');status.id='rolePermissionSearchStatus';status.setAttribute('role','status');
  wrapper.append(label,search,status);list.before(wrapper);
 }
 search.value='';
 search.oninput=()=>{const query=search.value.trim().toLocaleLowerCase(),rows=[...list.querySelectorAll('label')];let visible=0;
  rows.forEach(row=>{const identifier=row.querySelector('input')?.name||'';row.hidden=!(row.textContent+' '+identifier).toLocaleLowerCase().includes(query);if(!row.hidden)visible++;});
  status.textContent=text('roles.searchMatches','Matching permissions: ')+visible+' / '+rows.length;
 };
 search.oninput();
}
