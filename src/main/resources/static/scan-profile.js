/* Scan profile of the tenant: how scanned pages are rendered and prepared for text recognition. */
(() => {
 const node = (tag, text) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; return n; };
 async function build(host) {
  const box = node('section'); box.className = 'scan-profile'; box.id = 'scanProfileBox';
  const message = node('p', ''); message.setAttribute('role', 'status');
  const select = document.createElement('select'); select.name = 'scanProfile';
  const description = node('small', '');
  const save = node('button', 'Scan-Profil speichern'); save.type = 'button';
  const label = node('label', 'Profil '); label.append(select);
  box.append(node('h3', 'Scan-Profil'), node('p', 'Bestimmt, wie eingescannte Seiten für die Texterkennung aufbereitet werden. Die Änderung gilt für ab jetzt erkannte Dokumente; vorhandene werden über „Neu erkennen“ mit dem neuen Profil erkannt.'), label, description, save, message);
  host.append(box);
  let view = null;
  const describe = () => { const p = view?.available?.find(x => x.id === select.value); description.textContent = p ? `${p.description} (${p.renderDpi} DPI)` : ''; };
  select.onchange = describe;
  async function load() {
   try {
    view = await json('/api/scan-profile');
    select.replaceChildren();
    view.available.forEach(p => { const o = node('option', p.label); o.value = p.id; select.append(o); });
    select.value = view.current; describe();
    message.textContent = view.changedBy ? `Zuletzt geändert von ${view.changedBy}.` : '';
   } catch (error) { message.textContent = error.message; }
  }
  save.onclick = async () => {
   if (save.disabled) return; save.disabled = true;
   try { view = await json('/api/scan-profile', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ profile: select.value }) }); message.textContent = 'Scan-Profil gespeichert.'; }
   catch (error) { message.textContent = error.message; }
   finally { save.disabled = false; }
  };
  await load();
 }
 globalThis.LcScanProfile = { build };
 if (typeof document === 'undefined' || !document.body) return;
 const wait = setInterval(() => {
  const host = document.querySelector('#tenantSection');
  if (host && typeof can === 'function' && typeof currentUser !== 'undefined' && currentUser) {
   clearInterval(wait);
   if (can('SETTINGS_MANAGE') && !host.querySelector('#scanProfileBox')) build(host);
  }
 }, 500);
})();
