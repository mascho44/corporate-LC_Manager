/* Settings page "Korrespondenzbanken": BIC, country, channels, routing and contact of the banks the tenant works with. */
async function setupCorrespondentBanks() {
 if (typeof can !== 'function' || !can('SETTINGS_MANAGE')) return;
 let banks, channels;
 try { [banks, channels] = await Promise.all([json('/api/correspondent-banks'), json('/api/correspondent-banks/channels')]); } catch { return; }
 const node = (tag, value, cls) => { const n = document.createElement(tag); if (value !== undefined) n.textContent = value; if (cls) n.className = cls; return n; };
 const channelName = code => tx('cb.channel.' + code, code);
 const nav = node('button', tx('cb.nav', 'Korrespondenzbanken')); nav.type = 'button'; nav.id = 'appNavCorrespondent'; nav.dataset.i18n = 'cb.nav';
 document.querySelector('#appNav nav').append(nav);
 const section = document.createElement('section'); section.id = 'correspondentSection'; section.className = 'panel hidden';
 const message = node('p', ''); message.setAttribute('role', 'status');
 const list = document.createElement('div'); const formBox = document.createElement('div');
 section.append(node('h2', tx('cb.title', 'Korrespondenzbanken')), node('p', tx('cb.intro', 'Banken, mit denen Sie Akkreditive und Nachrichten austauschen: BIC, Land, Kommunikationswege, Weiterleitung und Ansprechpartner.')), message, list, formBox);
 document.querySelector('main').append(section);

 function draw() {
  list.replaceChildren();
  if (!banks.length) list.append(node('p', tx('cb.none', 'Noch keine Korrespondenzbanken.')));
  banks.forEach(bank => {
   const row = node('article', undefined, 'membership-row cb-bank' + (bank.active ? '' : ' inactive'));
   const text = node('div');
   const place = [bank.city, bank.country].filter(Boolean).join(', ');
   text.append(node('b', bank.name + ' · ' + bank.bic + (bank.active ? '' : ' · ' + tx('cb.inactive', 'inaktiv'))));
   const details = [place, bank.channels.map(channelName).join(', '), bank.routingBic ? tx('cb.routedVia', 'über {bic}', { bic: bank.routingBic }) : '', bank.contactEmail].filter(Boolean).join(' · ');
   if (details) text.append(node('small', details));
   const edit = node('button', tx('cb.edit', 'Bearbeiten'), 'secondary'); edit.type = 'button'; edit.onclick = () => form(bank);
   const del = node('button', tx('cb.delete', 'Löschen'), 'secondary'); del.type = 'button';
   del.onclick = async () => {
    if (!await confirmAction(tx('cb.confirmDelete', 'Korrespondenzbank „{name}“ löschen?', { name: bank.name }))) return;
    try { await json('/api/correspondent-banks/' + bank.id, { method: 'DELETE' }); banks = banks.filter(b => b.id !== bank.id); message.textContent = tx('cb.deleted', 'Korrespondenzbank gelöscht.'); draw(); } catch (error) { message.textContent = error.message; }
   };
   row.append(text, edit, del); list.append(row);
  });
  const add = node('button', tx('cb.add', 'Korrespondenzbank hinzufügen')); add.type = 'button'; add.className = 'cb-add'; add.onclick = () => form(null); list.append(add);
 }
 function form(bank) {
  formBox.replaceChildren(); message.textContent = '';
  const f = document.createElement('form'); f.className = 'form-grid cb-form';
  const field = (label, control) => { const l = node('label', label); l.append(control); f.append(l); return control; };
  const input = (label, max, value) => { const c = field(label, document.createElement('input')); c.maxLength = max; c.value = value || ''; return c; };
  const bic = input(tx('cb.bic', 'BIC'), 11, bank?.bic); bic.required = true;
  const name = input(tx('cb.name', 'Name'), 140, bank?.name); name.required = true;
  const country = input(tx('cb.country', 'Land (ISO, z. B. DE)'), 2, bank?.country);
  const city = input(tx('cb.city', 'Ort'), 80, bank?.city);
  const routing = input(tx('cb.routing', 'Weiterleitung über BIC (optional)'), 11, bank?.routingBic);
  const email = input(tx('cb.email', 'E-Mail'), 200, bank?.contactEmail); email.type = 'email';
  const phone = input(tx('cb.phone', 'Telefon'), 50, bank?.contactPhone);
  const preferred = document.createElement('select'); const none = node('option', tx('cb.noPreferred', 'Kein bevorzugter Weg')); none.value = ''; preferred.append(none);
  channels.channels.forEach(c => { const o = node('option', channelName(c)); o.value = c; preferred.append(o); });
  preferred.value = bank?.preferredChannel || '';
  const channelBox = node('fieldset', undefined, 'cb-channels'); channelBox.append(node('legend', tx('cb.channels', 'Kommunikationswege')));
  const boxes = channels.channels.map(c => { const box = document.createElement('input'); box.type = 'checkbox'; box.value = c; box.checked = Boolean(bank?.channels.includes(c)); const l = node('label', channelName(c)); l.prepend(box); channelBox.append(l); return box; });
  f.append(channelBox); field(tx('cb.preferred', 'Bevorzugter Weg'), preferred);
  const notes = field(tx('cb.notes', 'Hinweise'), document.createElement('textarea')); notes.maxLength = 1000; notes.rows = 3; notes.value = bank?.notes || '';
  const active = document.createElement('input'); active.type = 'checkbox'; active.checked = bank ? bank.active : true; const activeLabel = node('label', tx('cb.active', 'Aktiv')); activeLabel.prepend(active); f.append(activeLabel);
  const save = node('button', tx('cb.save', 'Speichern')); save.type = 'submit'; const cancel = node('button', tx('cb.cancel', 'Abbrechen'), 'secondary'); cancel.type = 'button'; cancel.onclick = () => formBox.replaceChildren();
  const bar = document.createElement('div'); bar.className = 'cb-actions'; bar.append(save, cancel); f.append(bar);
  f.onsubmit = async event => {
   event.preventDefault();
   const body = { bic: bic.value, name: name.value, country: country.value, city: city.value, routingBic: routing.value, contactEmail: email.value, contactPhone: phone.value, preferredChannel: preferred.value || null, channels: boxes.filter(b => b.checked).map(b => b.value), notes: notes.value, active: active.checked };
   try {
    const saved = await json(bank ? '/api/correspondent-banks/' + bank.id : '/api/correspondent-banks', { method: bank ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    banks = (bank ? banks.map(b => b.id === saved.id ? saved : b) : [...banks, saved]).sort((a, b) => a.name.localeCompare(b.name));
    formBox.replaceChildren(); message.textContent = tx('cb.saved', 'Korrespondenzbank gespeichert.'); draw();
   } catch (error) { message.textContent = error.message; }
  };
  formBox.append(f);
 }
 nav.onclick = () => { appNavigate('correspondent', nav); draw(); };
 draw();
}
