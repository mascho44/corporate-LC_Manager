let ossComponents=[];
const ossRows=document.querySelector('#ossRows'),ossStatus=document.querySelector('#ossStatus');
function ossText(value){return String(value||'').replace(/[&<>"']/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));}
function ossLink(url,label){return /^https?:\/\//.test(url||'')?`<a href="${ossText(url)}" target="_blank" rel="noopener noreferrer">${ossText(label)}</a>`:ossText(label);}
function renderOss(){
 const query=document.querySelector('#ossSearch').value.trim().toLocaleLowerCase('de');
 const rows=ossComponents.filter(component=>[component.name,component.coordinate,component.version,...component.licenses.map(license=>license.name)].join(' ').toLocaleLowerCase('de').includes(query));
 ossRows.innerHTML=rows.map(component=>`<tr><td>${ossLink(component.projectUrl,component.name)}<small class="info-coordinate">${ossText(component.coordinate)}</small></td><td>${ossText(component.version)}</td><td>${component.licenses.map(license=>ossLink(license.url,license.name)).join('<br>')}</td><td>${ossLink(component.metadataUrl,'Maven-Metadaten')}</td></tr>`).join('');
 ossStatus.textContent=`${rows.length} von ${ossComponents.length} Laufzeitbibliotheken`;
}
document.querySelector('#ossSearch').addEventListener('input',renderOss);
fetch('/oss-components.json').then(response=>{if(!response.ok)throw new Error('Bibliotheken konnten nicht geladen werden.');return response.json();}).then(inventory=>{ossComponents=inventory.components;document.querySelector('#applicationVersion').textContent=inventory.applicationVersion;renderOss();}).catch(()=>{ossStatus.textContent='Bibliotheken konnten nicht geladen werden. Die JSON-Komponentenliste und Originalhinweise stehen über die Links oben bereit.';});
