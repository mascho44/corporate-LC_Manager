/* Read-only platform inventory. No purge or mutation action is provided. */
function renderTenantInventory(container,preview){
 const t=(key,fallback)=>{const v=globalThis.LcI18n?.t('inventory.'+key);return v&&v!=='inventory.'+key?v:fallback;};
 const node=(tag,value)=>{const n=document.createElement(tag);n.textContent=value;return n;};
 container.replaceChildren();container.className='tenant-inventory';
 container.append(node('h4',t('title','Inventory and deletion preview')+' · '+preview.tenantName));
 container.append(node('p',t('notice','Read-only snapshot. Deletion is unavailable. Global user accounts, credentials and avatars are excluded.')));
 container.append(node('p',t('observed','Observed at')+': '+new Date(preview.observedAt).toLocaleString()+' · '+t('bytes','Stored file bytes')+': '+preview.knownBinaryBytes.toLocaleString()+' · '+t('shared','Members also recorded in other tenants')+': '+preview.sharedMemberships));
 container.append(node('p',t('sizeNotice','File sizes cover database binary originals only, not text, indexes, backups or temporary files. Counts may overlap conceptually; they are not a deletion total.')));
 const table=document.createElement('table');table.append(node('caption',t('categories','Data categories')));const head=document.createElement('thead'),headers=document.createElement('tr');
 for(const [key,label] of [['category','Category'],['records','Records'],['bytes','Stored file bytes']]){const cell=node('th',t(key,label));cell.scope='col';headers.append(cell);}head.append(headers);table.append(head);const body=document.createElement('tbody');
 for(const category of preview.categories){const row=document.createElement('tr');row.append(node('td',t('category.'+category.key,category.key)),node('td',category.records.toLocaleString()),node('td',category.binaryBytes==null?t('notMeasured','Not measured'):category.binaryBytes.toLocaleString()));body.append(row);}table.append(body);container.append(table);
 container.append(node('h4',t('jobs','Known pending work')));const jobs=preview.jobs;
 container.append(node('p',t('ocr','Inbox OCR queued/processing')+': '+jobs.inboxExtraction+' · '+t('outbox','Integration pending/dead letter')+': '+jobs.outbox+' · '+t('mail','Queued invitation emails')+': '+jobs.invitationMail));
 container.append(node('h4',t('blockers','Unresolved deletion prerequisites')));const blockers=document.createElement('ul');for(const code of preview.blockers)blockers.append(node('li',t('blocker.'+code,code)));container.append(blockers);
}
