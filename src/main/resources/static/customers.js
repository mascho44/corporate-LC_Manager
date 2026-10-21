/* Customer master (settings) and the customer block of submission letters in the inbox: what the letter says, the suggested customer, and a manual choice. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const label = { customerName: tx('cul.7c56a3','Kunde'), customerNumber: tx('cul.21ade1','Kundennummer'), accountNumber: tx('cul.6b882a','Kontonummer'), iban: 'IBAN', ibanCandidate: tx('cul.629a55','IBAN (nicht prüfbar – bitte am Original prüfen)'), bic: 'BIC',
  lcReference: tx('cul.a4e303','Akkreditivnummer'), yourReference: tx('cul.05c3bb','Ihre Referenz'), applicant: tx('cul.affebf','Auftraggeber (Akkreditiv)'), issuingBank: tx('cul.56781e','Eröffnende Bank'), documentValue: tx('cul.87be41','Dokumentengegenwert') };

 /** The block shown for one submission letter. `customers` is the master list for the manual choice. */
 function blockHtml(entry, customers) {
  const batch = entry.via === 'BATCH';
  const read = Object.entries(label).filter(([key]) => entry.fields?.[key]).map(([key, text]) => `<dt>${text}</dt><dd>${esc(entry.fields[key])}</dd>`).join('');
  const found = batch ? '' : read ? `<dl class="customer-facts">${read}</dl>` : '<p>'+tx('cuh.575e72','Im Einreichungsschreiben wurden keine Kundenangaben erkannt.')+'</p>';
  const best = entry.candidates?.[0];
  let status;
  if (entry.assigned) status = `<p class="customer-assigned">${tx('cuh.fdebb3','✓ Kunde:')} <b>${esc(entry.assigned.name)}</b>${entry.assigned.method === 'SUGGESTION' ? tx('cul.4f07d9',' (Vorschlag bestätigt)') : tx('cul.76a8bc',' (manuell gewählt)')}</p>`;
  else if (batch && best) status = `<p class="customer-suggestion">${tx('cuh.127302','Aus dem Einreichungsschreiben dieser Lieferung (')}${esc(best.reason)}): <b>${esc(best.name)}</b> <button type="button" data-customer-confirm="${esc(best.customerId)}">${tx('cuh.618f83','Bestätigen')}</button></p>`;
  else if (best && entry.clear) status = `<p class="customer-suggestion">${tx('cuh.0b9afb','Erkannt:')} <b>${esc(best.name)}</b> ${tx('cuh.82d749','(über')} ${esc(best.reason)}) <button type="button" data-customer-confirm="${esc(best.customerId)}">${tx('cuh.618f83','Bestätigen')}</button></p>`;
  else if (best) status = `<p class="customer-suggestion">${tx('cuh.860686','Mögliche Kunden:')} ${entry.candidates.map(c => `<b>${esc(c.name)}</b> (${esc(c.reason)})`).join(', ')}${tx('cux.pick','. Bitte auswählen.')}</p>`;
  else status = '<p class="customer-missing">'+tx('cuh.e61a6d','Kein Kunde erkannt. Bitte den Kunden auswählen.')+'</p>';
  const lcs = (entry.lcs || []).length
   ? `<p class="customer-lcs">${tx('cuh.200a96','Akten dieses Kunden:')} ${entry.lcs.map(l => `<button type="button" class="secondary" data-pick-lc="${esc(l.id)}" title="${tx('cuh.994b7e','LC-Akte in der Zuordnung auswählen')}">${esc(l.reference)}${l.byLetter ? tx('cul.708e6a',' · laut Schreiben') : l.linked ? tx('cul.2e4582',' · fest verknüpft') : ''}</button>`).join(' ')}</p>` : '';
  const options = (customers || []).map(c => `<option value="${esc(c.id)}"${entry.assigned?.customerId === c.id ? ' selected' : ''}>${esc(c.name)}${c.customerNumber ? ' · ' + esc(c.customerNumber) : ''}</option>`).join('');
  const choose = customers?.length
   ? `<label>${tx('cuh.4509b5','Kunde wählen')} <select data-customer-select><option value="">${tx('cuh.e42fc3','– bitte wählen –')}</option>${options}</select></label>${entry.assigned ? ' <button type="button" class="secondary" data-customer-clear>'+tx('cuh.25568a','Zuordnung entfernen')+'</button>' : ''}`
   : '<p><small>'+tx('cuh.d5148d','Im Kundenstamm (Einstellungen) ist noch kein Kunde angelegt.')+'</small></p>';
  return `<summary>${batch ? tx('cul.daf1e1','Kunde dieser Lieferung') : tx('cul.595de1','Einreichungsschreiben · Kunde')}</summary>${found}${status}${lcs}<p>${choose}</p><p class="customer-error" role="alert"></p>`;
 }
 globalThis.LcCustomers = { blockHtml };
 if (typeof document === 'undefined' || !document.body) return;

 /* ---- inbox block ---- */
 let cache = null, loading = false;
 async function load() {
  if (loading) return;
  loading = true;
  try { cache = { entries: await json('/api/inbox-customers'), customers: await json('/api/customers') }; }
  catch (error) { cache = null; }
  finally { loading = false; }
  apply();
 }
 function apply() {
  if (!cache) return;
  document.querySelectorAll('.inbox-item').forEach(article => {
   const id = article.dataset.inboxId, entry = cache.entries[id];
   let box = article.querySelector('.inbox-customer');
   if (!entry) { box?.remove(); return; }
   const open = box ? box.open : !entry.assigned;
   if (!box) { box = document.createElement('details'); box.className = 'inbox-customer'; (article.querySelector('.inbox-item-head') || article).after(box); }
   box.open = open; box.innerHTML = blockHtml(entry, cache.customers);
   const send = async (customerId) => {
    try { await json(`/api/inbox-customers/${encodeURIComponent(id)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ customerId }) }); await load(); }
    catch (error) { box.querySelector('.customer-error').textContent = error.message; }
   };
   box.querySelectorAll('[data-pick-lc]').forEach(pick => pick.addEventListener('click', () => {
    const select = article.querySelector('form[data-inbox-attach] select[name=lcId]');
    if (!select) { box.querySelector('.customer-error').textContent = tx('cul.f3c432','Das Zuordnungsformular ist gerade nicht verfügbar.'); return; }
    select.value = pick.dataset.pickLc; select.dispatchEvent(new Event('input', { bubbles: true })); select.dispatchEvent(new Event('change', { bubbles: true }));
    select.scrollIntoView({ block: 'center', behavior: 'smooth' }); select.focus({ preventScroll: true });
   }));
   box.querySelector('[data-customer-confirm]')?.addEventListener('click', event => send(event.currentTarget.dataset.customerConfirm));
   box.querySelector('[data-customer-select]')?.addEventListener('change', event => { if (event.target.value) send(event.target.value); });
   box.querySelector('[data-customer-clear]')?.addEventListener('click', async () => {
    try { await json(`/api/inbox-customers/${encodeURIComponent(id)}`, { method: 'DELETE' }); await load(); } catch (error) { box.querySelector('.customer-error').textContent = error.message; }
   });
  });
 }
 let known = '';
 setInterval(() => {
  const section = document.querySelector('#inboxSection');
  if (!section || section.classList.contains('hidden') || typeof can !== 'function' || !can('DOCUMENT_UPLOAD')) return;
  const ids = [...document.querySelectorAll('.inbox-item')].map(a => a.dataset.inboxId).join(',');
  if (ids !== known) { known = ids; load(); }
  else if (cache && document.querySelectorAll('.inbox-item').length && [...document.querySelectorAll('.inbox-item')].some(a => cache.entries[a.dataset.inboxId] && !a.querySelector('.inbox-customer'))) apply();
 }, 1500);

 /* ---- customer master in the tenant settings ---- */
 async function buildMaster(host) {
  const box = document.createElement('section'); box.className = 'settings-box'; box.id = 'customerMasterBox';
  const message = document.createElement('p'); message.className = 'settings-status'; message.setAttribute('role', 'status');
  const list = document.createElement('div'); list.className = 'settings-list';
  const form = document.createElement('form'); form.className = 'lc-form';
  const field = (name, text, max) => { const l = document.createElement('label'); l.textContent = text + ' '; const i = document.createElement('input'); i.name = name; i.maxLength = max; l.append(i); return l; };
  form.append(field('name', tx('cul.49ee30','Name'), 255), field('customerNumber', tx('cul.21ade1','Kundennummer'), 60), field('accountNumber', tx('cul.6b882a','Kontonummer'), 60), field('iban', 'IBAN', 42), field('bic', 'BIC', 11));
  const save = document.createElement('button'); save.type = 'submit'; save.textContent = tx('cul.652b9d','Kunde speichern'); form.append(save);
  const heading = document.createElement('h3'); heading.textContent = 'Kundenstamm';
  const intro = document.createElement('p'); intro.textContent = 'Kunden mit Kunden-, Konto- und IBAN-Angaben. Sie dienen dazu, Einreichungsschreiben im Posteingang einem Kunden zuzuordnen. Weitere Quellen (z. B. aus einem Kernsystem) können später angebunden werden.';
  box.append(heading, intro, form, message, list); host.append(box);
  let editing = null;
  const render = customers => {
   list.replaceChildren();
   if (!customers.length) { const p = document.createElement('p'); p.textContent = 'Noch kein Kunde angelegt.'; list.append(p); }
   customers.forEach(c => {
    const row = document.createElement('div'); row.className = 'settings-row';
    const text = document.createElement('span'); text.textContent = [c.name, c.customerNumber && 'Kd-Nr. ' + c.customerNumber, c.accountNumber && tx('cul.27e4ce','Konto ') + c.accountNumber, c.iban].filter(Boolean).join(' · '); row.append(text);
    const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'secondary'; edit.textContent = tx('cul.5ad05d','Bearbeiten');
    edit.onclick = () => { editing = c.id; ['name', 'customerNumber', 'accountNumber', 'iban', 'bic'].forEach(k => { form.elements[k].value = c[k] || ''; }); save.textContent = 'Änderung speichern'; form.elements.name.focus(); };
    const del = document.createElement('button'); del.type = 'button'; del.className = 'danger'; del.textContent = tx('cul.1010b0','Löschen');
    del.onclick = async () => {
     if (typeof confirmAction === 'function' && !await confirmAction(tx('cut.7e7e1c','Kunde „{p0}" löschen? Zuordnungen im Posteingang werden entfernt.',{p0:c.name}))) return;
     try { await json('/api/customers/' + encodeURIComponent(c.id), { method: 'DELETE' }); message.textContent = tx('cul.b44da5','Kunde gelöscht.'); render(await json('/api/customers')); } catch (error) { message.textContent = error.message; }
    };
    row.append(edit, del); list.append(row);
   });
  };
  form.onsubmit = async event => {
   event.preventDefault(); save.disabled = true; message.textContent = '';
   const body = Object.fromEntries(new FormData(form).entries());
   try {
    await json(editing ? '/api/customers/' + encodeURIComponent(editing) : '/api/customers', { method: editing ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    form.reset(); editing = null; save.textContent = tx('cul.652b9d','Kunde speichern'); message.textContent = tx('cul.d88dae','Gespeichert.'); render(await json('/api/customers'));
   } catch (error) { message.textContent = error.message; } finally { save.disabled = false; }
  };
  try { render(await json('/api/customers')); } catch (error) { message.textContent = error.message; }
 }
 const wait = setInterval(() => {
  const host = document.querySelector('#tenantSection');
  if (host && typeof can === 'function' && typeof currentUser !== 'undefined' && currentUser) {
   clearInterval(wait);
   if (can('SETTINGS_MANAGE') && !host.querySelector('#customerMasterBox')) buildMaster(host);
  }
 }, 500);
})();
